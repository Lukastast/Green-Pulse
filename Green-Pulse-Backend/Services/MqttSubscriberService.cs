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
        private IMqttClient? _mqttClient;
        
        // MQTT Configuration
        private const string BROKER = "localhost";  // Change to "10.42.131.124" if running outside the broker machine
        private const int PORT = 8883;
        private const string USERNAME = "app_user";
        private const string PASSWORD = "appsecure456";
        private const string TOPIC = "greenpulse/#";

        public MqttSubscriberService(ILogger<MqttSubscriberService> logger)
        {
            _logger = logger;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            try
            {
                await ConnectToMqttBroker(stoppingToken);
                
                // Keep the service running
                while (!stoppingToken.IsCancellationRequested)
                {
                    await Task.Delay(1000, stoppingToken);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error in MQTT Subscriber Service");
            }
        }

        private async Task ConnectToMqttBroker(CancellationToken stoppingToken)
        {
            var factory = new MqttClientFactory();
            _mqttClient = factory.CreateMqttClient();

            // Configure MQTT options for MQTTnet 5.x
            var tlsOptions = new MqttClientTlsOptions
            {
                UseTls = true,
                SslProtocol = SslProtocols.Tls12 | SslProtocols.Tls13,
                CertificateValidationHandler = _ => true  // Skip certificate validation for self-signed certs
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

            // Set up message handler
            _mqttClient.ApplicationMessageReceivedAsync += OnMessageReceived;

            // Set up connection handler
            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("✅ Connected to MQTT broker at {Broker}:{Port}", BROKER, PORT);

                // Subscribe to topics (MQTTnet 5.x API)
                var subscribeOptions = new MqttClientSubscribeOptionsBuilder()
                    .WithTopicFilter(f => f
                        .WithTopic(TOPIC)
                        .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce))
                    .Build();

                await _mqttClient.SubscribeAsync(subscribeOptions, stoppingToken);

                _logger.LogInformation("📡 Subscribed to topic: {Topic}", TOPIC);
            };

            // Set up disconnection handler
            _mqttClient.DisconnectedAsync += async e =>
            {
                _logger.LogWarning("❌ Disconnected from MQTT broker. Reason: {Reason}", e.Reason);
                
                if (!stoppingToken.IsCancellationRequested)
                {
                    _logger.LogInformation("Attempting to reconnect in 5 seconds...");
                    await Task.Delay(TimeSpan.FromSeconds(5), stoppingToken);
                    
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

            // Connect to broker
            _logger.LogInformation("Connecting to MQTT broker at {Broker}:{Port}...", BROKER, PORT);
            await _mqttClient.ConnectAsync(options, stoppingToken);
        }

        private Task OnMessageReceived(MqttApplicationMessageReceivedEventArgs e)
        {
            try
            {
                var payload = Encoding.UTF8.GetString(e.ApplicationMessage.Payload);
                var topic = e.ApplicationMessage.Topic;

                _logger.LogInformation("📨 Received message on {Topic}", topic);

                // Parse JSON payload
                using var jsonDoc = JsonDocument.Parse(payload);
                var eventType = jsonDoc.RootElement.GetProperty("event").GetString();

                // Handle different event types
                switch (eventType)
                {
                    case "sensor_update":
                        HandleSensorUpdate(jsonDoc.RootElement);
                        break;
                    
                    case "plant_added":
                        HandlePlantAdded(jsonDoc.RootElement);
                        break;
                    
                    case "plant_watered":
                        HandlePlantWatered(jsonDoc.RootElement);
                        break;
                    
                    case "ph_adjusted":
                        HandlePhAdjusted(jsonDoc.RootElement);
                        break;
                    
                    case "temp_adjusted":
                        HandleTempAdjusted(jsonDoc.RootElement);
                        break;
                    
                    case "plant_dead":
                        HandlePlantDead(jsonDoc.RootElement);
                        break;
                    
                    case "simulator_started":
                        HandleSimulatorStarted(jsonDoc.RootElement);
                        break;
                    
                    default:
                        _logger.LogWarning("Unknown event type: {EventType}", eventType);
                        break;
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing MQTT message");
            }

            return Task.CompletedTask;
        }

        private void HandleSensorUpdate(JsonElement data)
        {
            var plantId = data.GetProperty("plantId").GetString();
            var humidity = data.GetProperty("humidity").GetDouble();
            var ph = data.GetProperty("ph").GetDouble();
            var temperature = data.GetProperty("temperature").GetDouble();
            var alive = data.GetProperty("alive").GetBoolean();

            _logger.LogInformation(
                "🌱 Sensor Update - Plant: {PlantId}, Humidity: {Humidity}%, pH: {Ph}, Temp: {Temp}°C, Alive: {Alive}",
                plantId, humidity, ph, temperature, alive
            );

            // TODO: Store in database or update application state
        }

        private void HandlePlantAdded(JsonElement data)
        {
            var plantId = data.GetProperty("plantId").GetString();
            var type = data.GetProperty("type").GetString();
            
            _logger.LogInformation("🌱 New plant added: {PlantId} ({Type})", plantId, type);
            
            // TODO: Add plant to database
        }

        private void HandlePlantWatered(JsonElement data)
        {
            var plantId = data.GetProperty("plantId").GetString();
            var humidity = data.GetProperty("humidity").GetDouble();
            
            _logger.LogInformation("💧 Plant watered: {PlantId}, New humidity: {Humidity}%", plantId, humidity);
            
            // TODO: Log watering event
        }

        private void HandlePhAdjusted(JsonElement data)
        {
            var plantId = data.GetProperty("plantId").GetString();
            var ph = data.GetProperty("ph").GetDouble();
            
            _logger.LogInformation("⚗️ pH adjusted: {PlantId}, New pH: {Ph}", plantId, ph);
            
            // TODO: Log pH adjustment
        }

        private void HandleTempAdjusted(JsonElement data)
        {
            var plantId = data.GetProperty("plantId").GetString();
            var temp = data.GetProperty("temperature").GetDouble();
            
            _logger.LogInformation("🌡️ Temperature adjusted: {PlantId}, New temp: {Temp}°C", plantId, temp);
            
            // TODO: Log temperature adjustment
        }

        private void HandlePlantDead(JsonElement data)
        {
            var plantId = data.GetProperty("plantId").GetString();
            
            _logger.LogWarning("💀 Plant died: {PlantId}", plantId);
            
            // TODO: Mark plant as dead in database
        }

        private void HandleSimulatorStarted(JsonElement data)
        {
            var numPlants = data.GetProperty("num_plants").GetInt32();
            
            _logger.LogInformation("🚀 Simulator started with {NumPlants} plants", numPlants);
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