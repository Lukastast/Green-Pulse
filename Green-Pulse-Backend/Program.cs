var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddControllers();
builder.Services.AddHostedService<Green_Pulse_Backend.Services.MqttSubscriberService>();

var app = builder.Build();

app.MapControllers();
app.Run();
