using Google.Cloud.Firestore;
using MQTTnet;
using MQTTnet.Protocol;
using System.Security.Authentication;
using System.Text;
using System.Text.Json;

namespace GreenPulse.Services
{
    public class MqttSubscriberService : BackgroundService
    {
        private readonly ILogger<MqttSubscriberService> _logger;
        private readonly FirestoreDb _firestore;
        private IMqttClient? _mqttClient;

        // MQTT Config
        private const string BROKER = "localhost"; 
        private const int PORT = 8883;
        private const string USERNAME = "app_user";
        private const string PASSWORD = "appsecure456";
        private const string TOPIC = "greenpulse/#";

        public MqttSubscriberService(
            ILogger<MqttSubscriberService> logger,
            FirestoreDb firestore)
        {
            _logger = logger;
            _firestore = firestore;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            try
            {
                await ConnectToMqttBroker(stoppingToken);

                while (!stoppingToken.IsCancellationRequested)
                {
                    await Task.Delay(1000, stoppingToken);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "MQTT Service crashed");
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
                .WithClientId($"greenpulse-dotnet-{Guid.NewGuid()}")
                .WithCleanSession()
                .WithKeepAlivePeriod(TimeSpan.FromSeconds(60))
                .WithTimeout(TimeSpan.FromSeconds(10))
                .WithTlsOptions(tlsOptions)
                .Build();

            _mqttClient.ApplicationMessageReceivedAsync += OnMessageReceived;

            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("✅ MQTT connected to {Broker}:{Port}", BROKER, PORT);

                var subscribeOptions = new MqttClientSubscribeOptionsBuilder()
                    .WithTopicFilter(f => f.WithTopic(TOPIC).WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce))
                    .Build();

                await _mqttClient.SubscribeAsync(subscribeOptions, stoppingToken);
                _logger.LogInformation("📡 Subscribed to MQTT topic: {Topic}", TOPIC);
            };

            _mqttClient.DisconnectedAsync += async e =>
            {
                _logger.LogWarning("❌ MQTT disconnected. Reason: {Reason}", e.Reason);

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
                        _logger.LogError(ex, "Reconnection failed.");
                    }
                }
            };

            _logger.LogInformation("Connecting to MQTT broker...");
            await _mqttClient.ConnectAsync(options, stoppingToken);
        }

        private Task OnMessageReceived(MqttApplicationMessageReceivedEventArgs e)
        {
            try
            {
                string payload = Encoding.UTF8.GetString(e.ApplicationMessage.Payload);
                string topic = e.ApplicationMessage.Topic;

                _logger.LogInformation("📨 Incoming MQTT message on {Topic}", topic);
                _logger.LogInformation("Payload: {Payload}", payload);

                using var json = JsonDocument.Parse(payload);
                string eventType = json.RootElement.GetProperty("event").GetString()!;

                switch (eventType)
                {
                    case "sensor_update":
                        _ = SaveSensorUpdate(json.RootElement);
                        break;

                    case "plant_added":
                        _ = SaveEventLog("plant_added", json.RootElement);
                        break;

                    case "plant_watered":
                        _ = SaveEventLog("plant_watered", json.RootElement);
                        break;

                    case "ph_adjusted":
                        _ = SaveEventLog("ph_adjusted", json.RootElement);
                        break;

                    case "temp_adjusted":
                        _ = SaveEventLog("temp_adjusted", json.RootElement);
                        break;

                    case "plant_dead":
                        _ = SaveEventLog("plant_dead", json.RootElement);
                        break;

                    default:
                        _logger.LogWarning("⚠ Unknown event: {EventType}", eventType);
                        break;
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error reading MQTT message");
            }

            return Task.CompletedTask;
        }

        // ---------------------------
        // FIRESTORE SAVE FUNCTIONS
        // ---------------------------

        private async Task SaveSensorUpdate(JsonElement data)
        {
            try
            {
                string plantId = data.GetProperty("plantId").GetString()!;
                double humidity = data.GetProperty("humidity").GetDouble();
                double ph = data.GetProperty("ph").GetDouble();
                double temp = data.GetProperty("temperature").GetDouble();
                bool alive = data.GetProperty("alive").GetBoolean();

                var docRef = _firestore
                    .Collection("plants")
                    .Document(plantId)
                    .Collection("sensor_updates")
                    .Document(); // auto-ID

                await docRef.SetAsync(new
                {
                    plantId,
                    humidity,
                    ph,
                    temperature = temp,
                    alive,
                    timestamp = Timestamp.GetCurrentTimestamp()
                });

                _logger.LogInformation("🔥 Saved sensor update for plant {PlantId}", plantId);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to save sensor update");
            }
        }

        private async Task SaveEventLog(string type, JsonElement data)
        {
            try
            {
                var docRef = _firestore
                    .Collection("event_logs")
                    .Document();

                await docRef.SetAsync(new
                {
                    type,
                    data = JsonSerializer.Deserialize<object>(data.GetRawText()),
                    timestamp = Timestamp.GetCurrentTimestamp()
                });

                _logger.LogInformation("📘 Saved event log: {Event}", type);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to save event log");
            }
        }

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
}
