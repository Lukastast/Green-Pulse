using System.Collections.Concurrent;
using System.Security.Authentication;
using System.Text;
using System.Text.Json;
using MQTTnet;
using MQTTnet.Protocol;

namespace GreenPulse.Services
{
    public class WateringService : BackgroundService
    {
        private readonly ILogger<WateringService> _logger;
        private IMqttClient? _mqttClient;
        private readonly ConcurrentDictionary<string, PlantStatus> _plantStatuses = new();

        // MQTT Config
        private const string BROKER = "localhost"; // or "10.42.131.124"
        private const int PORT = 8883;
        private const string USERNAME = "sensor_user";
        private const string PASSWORD = "securepass123";
        private const string SUBSCRIBE_TOPIC = "greenpulse/simulator";
        private const string PUBLISH_TOPIC_PREFIX = "greenpulse/humidity";

        // Watering thresholds
        private const double DRY_THRESHOLD = 30.0;
        private const double WET_THRESHOLD = 40.0;
        private const double WATERING_AMOUNT = 0.3;

        public WateringService(ILogger<WateringService> logger)
        {
            _logger = logger;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            try
            {
                await ConnectToMqttBroker(stoppingToken);

                // Main loop
                while (!stoppingToken.IsCancellationRequested)
                {
                    await CheckAndWaterPlants(stoppingToken);
                    await Task.Delay(10000, stoppingToken); // Check every 10 seconds
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "WateringService crashed");
            }
        }

        private async Task ConnectToMqttBroker(CancellationToken stoppingToken)
        {
            var factory = new MqttClientFactory();
            _mqttClient = factory.CreateMqttClient();

            var tlsOptions = new MqttClientTlsOptions
            {
                UseTls = true,
                SslProtocol = SslProtocols.Tls12 | SslProtocols.Tls13,
                CertificateValidationHandler = _ => true
            };

            var options = new MqttClientOptionsBuilder()
                .WithTcpServer(BROKER, PORT)
                .WithCredentials(USERNAME, PASSWORD)
                .WithClientId($"watering-service-{Guid.NewGuid()}")
                .WithCleanSession()
                .WithKeepAlivePeriod(TimeSpan.FromSeconds(60))
                .WithTimeout(TimeSpan.FromSeconds(10))
                .WithTlsOptions(tlsOptions)
                .Build();

            _mqttClient.ApplicationMessageReceivedAsync += OnMessageReceived;

            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("✅ WateringService connected to MQTT broker at {Broker}:{Port}", BROKER, PORT);

                var subscribeOptions = new MqttClientSubscribeOptionsBuilder()
                    .WithTopicFilter(f => f
                        .WithTopic(SUBSCRIBE_TOPIC)
                        .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce))
                    .Build();

                await _mqttClient.SubscribeAsync(subscribeOptions, stoppingToken);
                _logger.LogInformation("💧 WateringService subscribed to: {Topic}", SUBSCRIBE_TOPIC);
            };

            _mqttClient.DisconnectedAsync += async e =>
            {
                _logger.LogWarning("❌ WateringService disconnected. Reason: {Reason}", e.Reason);

                if (!stoppingToken.IsCancellationRequested)
                {
                    _logger.LogInformation("Reconnecting in 5 seconds...");
                    await Task.Delay(5000, stoppingToken);

                    try
                    {
                        await _mqttClient.ConnectAsync(options, stoppingToken);
                    }
                    catch (Exception ex)
                    {
                        _logger.LogError(ex, "Reconnection failed");
                    }
                }
            };

            _logger.LogInformation("WateringService connecting to MQTT broker...");
            await _mqttClient.ConnectAsync(options, stoppingToken);
        }

        private Task OnMessageReceived(MqttApplicationMessageReceivedEventArgs e)
        {
            try
            {
                string payload = Encoding.UTF8.GetString(e.ApplicationMessage.Payload);
                using var json = JsonDocument.Parse(payload);

                // Only process sensor_update events
                if (!json.RootElement.TryGetProperty("event", out var eventProp) ||
                    eventProp.GetString() != "sensor_update")
                {
                    return Task.CompletedTask;
                }

                if (!json.RootElement.TryGetProperty("plantId", out var plantIdProp) ||
                    !json.RootElement.TryGetProperty("humidity", out var humidityProp))
                {
                    return Task.CompletedTask;
                }

                string plantId = plantIdProp.GetString()!;
                double humidity = humidityProp.GetDouble();
                bool alive = json.RootElement.TryGetProperty("alive", out var aliveProp) && aliveProp.GetBoolean();

                // Skip dead plants
                if (!alive)
                {
                    return Task.CompletedTask;
                }

                // Update or create plant status
                var plant = _plantStatuses.GetOrAdd(plantId, new PlantStatus { PlantId = plantId });
                plant.Humidity = humidity;
                plant.LastUpdate = DateTime.UtcNow;

                // Hysteresis logic
                if (!plant.NeedsWater && humidity < DRY_THRESHOLD)
                {
                    plant.NeedsWater = true;
                    _logger.LogWarning("⚠️ {PlantId} is too dry! Humidity: {Humidity:F1}%", plantId, humidity);
                }
                else if (plant.NeedsWater && humidity > WET_THRESHOLD)
                {
                    plant.NeedsWater = false;
                    _logger.LogInformation("✅ {PlantId} humidity back to normal: {Humidity:F1}%", plantId, humidity);
                }

                // Publish humidity status to dashboard topic
                _ = PublishHumidityStatus(plantId, humidity);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing MQTT message in WateringService");
            }

            return Task.CompletedTask;
        }

        private async Task CheckAndWaterPlants(CancellationToken stoppingToken)
        {
            if (_mqttClient == null || !_mqttClient.IsConnected)
            {
                return;
            }

            foreach (var kvp in _plantStatuses)
            {
                var plant = kvp.Value;

                if (plant.NeedsWater)
                {
                    await SendWateringCommand(plant.PlantId, stoppingToken);
                }
            }
        }

        private async Task SendWateringCommand(string plantId, CancellationToken stoppingToken)
        {
            if (_mqttClient == null || !_mqttClient.IsConnected)
            {
                return;
            }

            try
            {
                var waterCommand = new
                {
                    action = "water",
                    plantId = plantId,
                    amount = WATERING_AMOUNT,
                    timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
                };

                var message = new MqttApplicationMessageBuilder()
                    .WithTopic(SUBSCRIBE_TOPIC)
                    .WithPayload(JsonSerializer.Serialize(waterCommand))
                    .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
                    .Build();

                await _mqttClient.PublishAsync(message, stoppingToken);
                _logger.LogInformation("💧 Sent watering command for {PlantId} (amount: {Amount})", plantId, WATERING_AMOUNT);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to send watering command for {PlantId}", plantId);
            }
        }

        private async Task PublishHumidityStatus(string plantId, double humidity)
        {
            if (_mqttClient == null || !_mqttClient.IsConnected)
            {
                return;
            }

            try
            {
                var humidityPayload = new
                {
                    plantId,
                    humidity,
                    timestamp = DateTime.UtcNow
                };

                var message = new MqttApplicationMessageBuilder()
                    .WithTopic($"{PUBLISH_TOPIC_PREFIX}/{plantId}")
                    .WithPayload(JsonSerializer.Serialize(humidityPayload))
                    .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
                    .Build();

                await _mqttClient.PublishAsync(message);
                _logger.LogDebug("📡 Published humidity status for {PlantId}: {Humidity:F1}%", plantId, humidity);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to publish humidity status for {PlantId}", plantId);
            }
        }

        public List<PlantStatus> GetStatuses() => _plantStatuses.Values.ToList();

        public override async Task StopAsync(CancellationToken cancellationToken)
        {
            if (_mqttClient != null && _mqttClient.IsConnected)
            {
                await _mqttClient.DisconnectAsync(cancellationToken: cancellationToken);
                _mqttClient.Dispose();
            }

            await base.StopAsync(cancellationToken);
        }
    }

    // Plant status model
    public class PlantStatus
    {
        public string PlantId { get; set; } = string.Empty;
        public double Humidity { get; set; }
        public bool NeedsWater { get; set; }
        public DateTime LastUpdate { get; set; }
    }
}