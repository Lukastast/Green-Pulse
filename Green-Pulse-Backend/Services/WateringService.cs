/* using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using MQTTnet;
using MQTTnet.Client;
using MQTTnet.Protocol;
using System.Net.Security;
using System.Security.Cryptography.X509Certificates;

namespace Green_Pulse_Backend.Services
{
    public class WateringService : BackgroundService
    {
        private readonly ILogger<WateringService> _logger;
        private readonly IMqttClient _mqttClient;
        private readonly ConcurrentDictionary<string, Models.PlantStatus> _plantStatuses = new();

        private const double DryThreshold = 30.0;
        private const double WetThreshold = 40.0;

        public WateringService(ILogger<WateringService> logger)
        {
            _logger = logger;
            _mqttClient = new MqttFactory().CreateMqttClient();
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            _logger.LogInformation("Starting WateringService...");

            // Opsæt MQTT options med TLS og username/password
            var options = new MqttClientOptionsBuilder()
                .WithClientId("watering_service")
                .WithTcpServer("10.42.131.125", 8883)
                .WithCredentials("sensor_user", "securepass123")
                .WithTls(o =>
                {
                    o.UseTls = true;
                    o.SslProtocol = System.Security.Authentication.SslProtocols.Tls12;

                    // Acceptér alle certifikater (inkl. selvsignerede)
                    o.CertificateValidationHandler = context =>
                    {
                        _logger.LogWarning($"⚠️ Accepting broker certificate for {context.Certificate.Subject}");
                        return true; // acceptér uanset hvad
                    };
                })
                .Build();


            _mqttClient.ConnectedAsync += async e =>
            {
                _logger.LogInformation("✅ Connected to MQTT broker!");
                // Subscribe til simulator topic
                await _mqttClient.SubscribeAsync(new MqttTopicFilterBuilder()
                    .WithTopic("greenpulse/simulator")
                    .WithAtLeastOnceQoS()
                    .Build());
            };

            _mqttClient.ApplicationMessageReceivedAsync += async e =>
            {
                try
                {
                    if (e.ApplicationMessage?.Payload == null || e.ApplicationMessage.Payload.Length == 0)
                        return;

                    string payloadString = System.Text.Encoding.UTF8.GetString(e.ApplicationMessage.Payload);
                    using var payload = JsonDocument.Parse(payloadString);

                    if (!payload.RootElement.TryGetProperty("plantId", out var idProp) ||
                        !payload.RootElement.TryGetProperty("humidity", out var humProp))
                        return;

                    string plantId = idProp.GetString()!;
                    double humidity = humProp.GetDouble();

                    var plant = _plantStatuses.GetOrAdd(plantId, new Models.PlantStatus { PlantId = plantId });
                    plant.Humidity = humidity;

                    // Hysterese
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

                    // Publicér humidity-status til EMQX-dashboard
                    var humidityPayload = new
                    {
                        plantId,
                        humidity = plant.Humidity,
                        timestamp = DateTime.UtcNow
                    };

                    var message = new MqttApplicationMessageBuilder()
                        .WithTopic($"greenpulse/humidity/{plantId}")
                        .WithPayload(JsonSerializer.Serialize(humidityPayload))
                        .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
                        .Build();

                    await _mqttClient.PublishAsync(message, stoppingToken);
                    _logger.LogInformation($"📡 Published humidity update for {plantId}: {plant.Humidity:F1}%");
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Error parsing MQTT payload");
                }
            };

            try
            {
                await _mqttClient.ConnectAsync(options, stoppingToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to connect to MQTT broker");
            }

            while (!stoppingToken.IsCancellationRequested)
            {
                await Task.Delay(10000, stoppingToken);
            }
        }

        public List<Models.PlantStatus> GetStatuses() => _plantStatuses.Values.ToList();
    }
}
 */