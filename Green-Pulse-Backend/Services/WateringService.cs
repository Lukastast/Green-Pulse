using System.Collections.Concurrent;
using System.Text.Json;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using MQTTnet;
using MQTTnet.Client;

namespace Green_Pulse_Backend.Services
{
    public class WateringService : BackgroundService
    {
        private readonly ILogger<WateringService> _logger;
        private readonly IMqttClient _mqttClient;
        private readonly ConcurrentDictionary<string, Models.PlantStatus> _plantStatuses = new();

        private const double DryThreshold = 30.0; // under 30% = for tørt
        private const double WetThreshold = 40.0; // over 40% = ok igen

        public WateringService(ILogger<WateringService> logger)
        {
            _logger = logger;
            var factory = new MqttFactory();
            _mqttClient = factory.CreateMqttClient();
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            _logger.LogInformation("Starting WateringService...");

            var options = new MqttClientOptionsBuilder()
                .WithClientId("watering_service")
                .WithTcpServer("localhost", 1883) // eller broker.emqx.io hvis ekstern
                .Build();

            _mqttClient.ApplicationMessageReceivedAsync += async e =>
            {
                try
                {
                    var payload = e.ApplicationMessage.Payload == null
                        ? null
                        : JsonDocument.Parse(e.ApplicationMessage.Payload);

                    if (payload == null) return;

                    if (payload.RootElement.TryGetProperty("plantId", out var idProp) &&
                        payload.RootElement.TryGetProperty("humidity", out var humProp))
                    {
                        string plantId = idProp.GetString()!;
                        double humidity = humProp.GetDouble();

                        var plant = _plantStatuses.GetOrAdd(plantId, new Models.PlantStatus { PlantId = plantId });
                        plant.Humidity = humidity;

                        // Hysterese check
                        if (!plant.NeedsWater && humidity < DryThreshold)
                        {
                            plant.NeedsWater = true;
                            _logger.LogWarning($"⚠️ {plantId} is too dry! Humidity: {humidity:F1}%");
                        }
                        else if (plant.NeedsWater && humidity > WetThreshold)
                        {
                            plant.NeedsWater = false;
                            _logger.LogInformation($"✅ {plantId} humidity back to normal: {humidity:F1}%");
                        }
                    }
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Error parsing MQTT payload");
                }
            };

            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("Connected to MQTT broker for WateringService!");
                await _mqttClient.SubscribeAsync("greenpulse/simulator");
            };

            await _mqttClient.ConnectAsync(options, stoppingToken);

            // Keep running
            while (!stoppingToken.IsCancellationRequested)
            {
                await Task.Delay(10000, stoppingToken);
            }
        }

        public List<Models.PlantStatus> GetStatuses()
        {
            return _plantStatuses.Values.ToList();
        }
    }
}
