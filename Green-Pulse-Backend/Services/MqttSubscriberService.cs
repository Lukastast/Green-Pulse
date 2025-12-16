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

        private const string BROKER = "localhost";
        private const int PORT = 1883;
        private const string USERNAME = "sensor_user";
        private const string PASSWORD = "securepass123";
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
                _logger.LogError(ex, "MQTT Subscriber Service crashed");
            }
        }

        private async Task ConnectToMqttBroker(CancellationToken stoppingToken)
        {
            var factory = new MqttClientFactory();
            _mqttClient = factory.CreateMqttClient();

            var options = new MqttClientOptionsBuilder()
                .WithTcpServer(BROKER, PORT)
                .WithCredentials(USERNAME, PASSWORD)
                .WithClientId($"greenpulse-subscriber-{Guid.NewGuid()}")
                .WithCleanSession()
                .WithKeepAlivePeriod(TimeSpan.FromSeconds(60))
                .WithTimeout(TimeSpan.FromSeconds(10))
                .Build();

            _mqttClient.ApplicationMessageReceivedAsync += OnMessageReceived;

            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("✅ MQTT Subscriber connected to {Broker}:{Port}", BROKER, PORT);

                var subscribeOptions = new MqttClientSubscribeOptionsBuilder()
                    .WithTopicFilter(f => f.WithTopic(TOPIC).WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce))
                    .Build();

                await _mqttClient.SubscribeAsync(subscribeOptions, stoppingToken);
                _logger.LogInformation("📡 Subscribed to: {Topic}", TOPIC);
            };

            _mqttClient.DisconnectedAsync += async e =>
            {
                _logger.LogWarning("❌ MQTT Subscriber disconnected. Reason: {Reason}", e.Reason);

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

            _logger.LogInformation("Connecting to MQTT broker...");
            await _mqttClient.ConnectAsync(options, stoppingToken);
        }

        private Task OnMessageReceived(MqttApplicationMessageReceivedEventArgs e)
        {
            try
            {
                string payload = Encoding.UTF8.GetString(e.ApplicationMessage.Payload);
                string topic = e.ApplicationMessage.Topic;

                _logger.LogDebug("📨 MQTT message on {Topic}", topic);

                using var json = JsonDocument.Parse(payload);

                if (!json.RootElement.TryGetProperty("event", out var eventProp))
                    return Task.CompletedTask;

                string eventType = eventProp.GetString()!;

                switch (eventType)
                {
                    case "status":
                        _ = SaveStatusToAllUsers(json.RootElement);
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
                        _logger.LogDebug("Unknown event: {EventType}", eventType);
                        break;
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing MQTT message");
            }

            return Task.CompletedTask;
        }

        // ---------------------------
        // FIRESTORE SAVE FUNCTIONS
        // ---------------------------

        private async Task SaveStatusToAllUsers(JsonElement data)
        {
            try
            {
                string plantId = data.GetProperty("plantId").GetString()!;
                double humidity = data.GetProperty("humidity").GetDouble();
                double ph = data.GetProperty("ph").GetDouble();
                double temp = data.GetProperty("temperature").GetDouble();
                bool alive = data.GetProperty("alive").GetBoolean();

                // Collection group query uden id-felt
                var snapshot = await _firestore.CollectionGroup("plants").GetSnapshotAsync();

                var plantDocs = snapshot.Documents
                    .Where(doc => doc.Id == plantId)
                    .ToList();

                if (plantDocs.Count == 0)
                {
                    _logger.LogDebug("Plant {PlantId} not found in any user's collection", plantId);
                    return;
                }

                _logger.LogDebug("Found {Count} instances of plant {PlantId}, saving to all",
                    plantDocs.Count, plantId);

                foreach (var plantDoc in plantDocs)
                {
                    try
                    {
                        var historyRef = plantDoc.Reference.Collection("history").Document();

                        await historyRef.SetAsync(new
                        {
                            alive,
                            humidity,
                            ph,
                            temperature = temp,
                            timestamp = Timestamp.GetCurrentTimestamp()
                        });

                        _logger.LogDebug("✅ Saved status to {Path}/history", plantDoc.Reference.Path);
                    }
                    catch (Exception ex)
                    {
                        _logger.LogError(ex, "Failed to save to {Path}", plantDoc.Reference.Path);
                    }
                }

                _logger.LogInformation("🔥 Saved status for plant {PlantId} to {Count} user(s)",
                    plantId, plantDocs.Count);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to save status to all users");
            }
        }

        private async Task SaveEventLog(string type, JsonElement data)
        {
            try
            {
                var docRef = _firestore.Collection("event_logs").Document();

                await docRef.SetAsync(new
                {
                    type,
                    data = JsonSerializer.Deserialize<object>(data.GetRawText()),
                    timestamp = Timestamp.GetCurrentTimestamp()
                });

                _logger.LogDebug("📘 Saved event log: {Event}", type);
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
