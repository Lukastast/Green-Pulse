using Green_Pulse_Backend.Models;
using GreenPulse.Services;


var builder = WebApplication.CreateBuilder(args);

// Add services to the container
builder.Services.AddControllers();

// Register services
builder.Services.AddSingleton<MqttSubscriberService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<MqttSubscriberService>());

/* builder.Services.AddSingleton<WateringService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<WateringService>()); */

//builder.Services.AddSingleton<PlantControlService>();
builder.Services.Configure<MqttSettings>(builder.Configuration.GetSection("Mqtt"));
// Swagger
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Initialize PlantControlService (for MQTT connection)
//var plantControlService = app.Services.GetRequiredService<PlantControlService>();
//await plantControlService.InitializeAsync();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseAuthorization();
app.MapControllers();
app.Run();
