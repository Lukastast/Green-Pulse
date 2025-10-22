using Green_Pulse_Backend.Services;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddControllers();

// Registrer MQTT-servicen som singleton, så vi kan bruge dens state i controller
builder.Services.AddSingleton<MqttSubscriberService>();
builder.Services.AddHostedService(provider => provider.GetService<MqttSubscriberService>());

// Tilføj Swagger
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Brug Swagger
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseAuthorization();

app.MapControllers();

app.Run();
