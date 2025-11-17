using Google.Cloud.Firestore;
using Green_Pulse_Backend.Models;
using GreenPulse.Services;

var builder = WebApplication.CreateBuilder(args);

// -----------------------------------------------------------
// FIRESTORE CONFIGURATION
// -----------------------------------------------------------

// Make sure the service account JSON is in the project root and named exactly "firestore-key.json"
Environment.SetEnvironmentVariable("GOOGLE_APPLICATION_CREDENTIALS",
    Path.Combine(builder.Environment.ContentRootPath, "firestore-key.json"));

// Register Firestore as a singleton service
builder.Services.AddSingleton(provider =>
{
    var projectId = "green-pulse-4ebdf"; // Your Firestore Project ID
    var firestore = FirestoreDb.Create(projectId);
    Console.WriteLine($"Firestore connected to project: {projectId}");
    return firestore;
});

// -----------------------------------------------------------
// ADD SERVICES / CONTROLLERS
// -----------------------------------------------------------

builder.Services.AddControllers();

// MQTT Subscriber service
builder.Services.AddSingleton<MqttSubscriberService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<MqttSubscriberService>());

// Swagger for API testing
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// -----------------------------------------------------------
// PIPELINE
// -----------------------------------------------------------

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseAuthorization();

app.MapControllers();

app.Run();
