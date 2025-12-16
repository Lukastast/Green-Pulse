using System.Collections.Concurrent;
using System.Security.Authentication;
using System.Text;
using System.Text.Json;
using MQTTnet;
using MQTTnet.Protocol;
using Google.Cloud.Firestore;

namespace GreenPulse.Services
{
    public class WateringService : BackgroundService
    {
        private readonly ILogger<WateringService> _logger;
        private readonly FirestoreDb _firestore;
        private IMqttClient? _mqttClient;
        private readonly ConcurrentDictionary<string, PlantStatus> _plantStatuses = new();

        // MQTT Config
        private const string BROKER = "192.168.0.124";
        private const int PORT = 1883;
        private const string USERNAME = "sensor_user";
        private const string PASSWORD = "securepass123";
        private const string SUBSCRIBE_TOPIC = "greenpulse/simulator";
        private const string WATER_COMMAND_TOPIC = "greenpulse/commands/water";

        // Plant type thresholds matching Python publisher
        private static readonly Dictionary<string, PlantThresholds> PlantTypes = new()
        {
            { "cactus", new PlantThresholds { IdealPh = 6.0, MinHumidity = 20, MaxHumidity = 40, DryRate = 0.5 } },
            { "tropical", new PlantThresholds { IdealPh = 6.5, MinHumidity = 60, MaxHumidity = 90, DryRate = 0.2 } },
            { "herb", new PlantThresholds { IdealPh = 6.8, MinHumidity = 50, MaxHumidity = 80, DryRate = 0.3 } },
            { "default", new PlantThresholds { IdealPh = 6.5, MinHumidity = 40, MaxHumidity = 70, DryRate = 0.3 } }
        };

        private const double WATERING_AMOUNT = 0.3;

        public WateringService(
            ILogger<WateringService> logger,
            FirestoreDb firestore)
        {
            _logger = logger;
            _firestore = firestore;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            try
            {
                // Load plant types from Firestore on startup
                await LoadPlantTypesFromFirestore();

                await ConnectToMqttBroker(stoppingToken);

                while (!stoppingToken.IsCancellationRequested)
                {
                    await CheckAndWaterPlants(stoppingToken);
                    await Task.Delay(10000, stoppingToken);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "WateringService crashed");
            }
        }

        private async Task LoadPlantTypesFromFirestore()
        {
            try
            {
                _logger.LogInformation("Loading plant types from Firestore using collection group...");

                var plantsQuery = _firestore.CollectionGroup("plants");
                var snapshot = await plantsQuery.GetSnapshotAsync();

                int totalLoaded = 0;

                foreach (var doc in snapshot.Documents)
                {
                    var plantId = doc.Id;
                    var data = doc.ToDictionary();

                    string plantType = data.ContainsKey("type") ? data["type"].ToString()! : "herb";

                    var pathParts = doc.Reference.Path.Split('/');
                    string environment = pathParts.Length >= 5 ? pathParts[3] : "Unknown";

                    var status = _plantStatuses.GetOrAdd(plantId, new PlantStatus
                    {
                        PlantId = plantId,
                        Type = plantType,
                        Environment = environment
                    });

                    status.Type = plantType;
                    status.Environment = environment;

                    totalLoaded++;
                    _logger.LogDebug("Loaded plant {PlantId} ({Type}) from {Environment}", plantId, plantType, environment);
                }

                _logger.LogInformation("✅ Loaded {Count} plants from Firestore (all users)", totalLoaded);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to load plant types from Firestore");
            }
        }

        private async Task ConnectToMqttBroker(CancellationToken stoppingToken)
        {
            var factory = new MqttClientFactory();
            _mqttClient = factory.CreateMqttClient();

            var options = new MqttClientOptionsBuilder()
                .WithTcpServer(BROKER, PORT)
                .WithCredentials(USERNAME, PASSWORD)
                .WithClientId($"watering-service-{Guid.NewGuid()}")
                .WithCleanSession()
                .WithKeepAlivePeriod(TimeSpan.FromSeconds(60))
                .WithTimeout(TimeSpan.FromSeconds(10))
                .Build();

            _mqttClient.ApplicationMessageReceivedAsync += OnMessageReceived;

            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("✅ WateringService connected to {Broker}:{Port}", BROKER, PORT);

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

                if (!json.RootElement.TryGetProperty("event", out var eventProp))
                    return Task.CompletedTask;

                string eventType = eventProp.GetString()!;
                switch (eventType)
                {
                    case "status":
                        ProcessStatus(json.RootElement);
                        break;
                    case "plant_added":
                        _logger.LogInformation("New plant added: {PlantId}", json.RootElement.GetProperty("plantId").GetString());
                        break;
                    case "plant_watered":
                        _logger.LogInformation("Plant watered: {PlantId}", json.RootElement.GetProperty("plantId").GetString());
                        break;
                    default:
                        _logger.LogDebug("Ignored unknown event: {EventType}", eventType);
                        break;
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing MQTT message in WateringService");
            }

            return Task.CompletedTask;
        }

        private void ProcessStatus(JsonElement json)
        {
            if (!json.TryGetProperty("plantId", out var plantIdProp) ||
                !json.TryGetProperty("humidity", out var humidityProp))
                return;

            string plantId = plantIdProp.GetString()!;
            double humidity = humidityProp.GetDouble();
            bool alive = json.TryGetProperty("alive", out var aliveProp) && aliveProp.GetBoolean();
            if (!alive) return;

            var plant = _plantStatuses.GetOrAdd(plantId, new PlantStatus
            {
                PlantId = plantId,
                Type = "herb"
            });

            plant.Humidity = humidity;
            plant.LastUpdate = DateTime.UtcNow;

            var thresholds = PlantTypes.GetValueOrDefault(plant.Type, PlantTypes["default"]);

            if (!plant.NeedsWater && humidity < thresholds.MinHumidity)
            {
                plant.NeedsWater = true;
                _logger.LogWarning("⚠️ {PlantId} ({Type}) is too dry! Humidity: {Humidity:F1}% (min: {Min}%)",
                    plantId, plant.Type, humidity, thresholds.MinHumidity);
            }
            else if (plant.NeedsWater && humidity > thresholds.MaxHumidity)
            {
                plant.NeedsWater = false;
                _logger.LogInformation("✅ {PlantId} ({Type}) humidity back to normal: {Humidity:F1}%",
                    plantId, plant.Type, humidity);
            }
        }

        private async Task CheckAndWaterPlants(CancellationToken stoppingToken)
        {
            if (_mqttClient == null || !_mqttClient.IsConnected)
                return;

            foreach (var kvp in _plantStatuses)
            {
                var plant = kvp.Value;
                if (plant.NeedsWater)
                    await SendWateringCommand(plant.PlantId, stoppingToken);
            }
        }

        private async Task SendWateringCommand(string plantId, CancellationToken stoppingToken)
        {
            if (_mqttClient == null || !_mqttClient.IsConnected)
                return;

            try
            {
                var waterCommand = new
                {
                    action = "water",
                    plantId = plantId,
                    amount = WATERING_AMOUNT
                };

                var message = new MqttApplicationMessageBuilder()
                    .WithTopic(WATER_COMMAND_TOPIC)
                    .WithPayload(JsonSerializer.Serialize(waterCommand))
                    .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
                    .Build();

                await _mqttClient.PublishAsync(message, stoppingToken);
                _logger.LogInformation("💧 Sent watering command for {PlantId} to {Topic}", plantId, WATER_COMMAND_TOPIC);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to send watering command for {PlantId}", plantId);
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

    public class PlantStatus
    {
        public string PlantId { get; set; } = string.Empty;
        public string Type { get; set; } = "herb";
        public string Environment { get; set; } = string.Empty;
        public double Humidity { get; set; }
        public bool NeedsWater { get; set; }
        public DateTime LastUpdate { get; set; }
    }

    public class PlantThresholds
    {
        public double IdealPh { get; set; }
        public double MinHumidity { get; set; }
        public double MaxHumidity { get; set; }
        public double DryRate { get; set; }
    }
}
