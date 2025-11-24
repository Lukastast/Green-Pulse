using Google.Cloud.Firestore;
using GreenPulse.Services;

var builder = WebApplication.CreateBuilder(args);

// -----------------------------------------------------------
// FIRESTORE CONFIGURATION
// -----------------------------------------------------------

// Read config from appsettings.json
var projectId = builder.Configuration["Firebase:ProjectId"];
var keyPath = builder.Configuration["Firebase:KeyPath"];

// Set environment variable for Firestore authentication
Environment.SetEnvironmentVariable(
    "GOOGLE_APPLICATION_CREDENTIALS",
    Path.Combine(builder.Environment.ContentRootPath, keyPath!)
);

// Register Firestore as a singleton service
builder.Services.AddSingleton(provider =>
{
    var firestore = FirestoreDb.Create(projectId);
    Console.WriteLine($"✅ Firestore connected to project: {projectId}");
    return firestore;
});

// -----------------------------------------------------------
// MQTT SERVICES
// -----------------------------------------------------------

// Register WateringService as singleton (so it can be injected in controller)
builder.Services.AddSingleton<WateringService>();

// Register as hosted background services
builder.Services.AddHostedService(provider => provider.GetRequiredService<WateringService>());
builder.Services.AddHostedService<MqttSubscriberService>();

// -----------------------------------------------------------
// ADD CONTROLLERS & SWAGGER
// -----------------------------------------------------------

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// Add CORS if needed for frontend
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin()
              .AllowAnyMethod()
              .AllowAnyHeader();
    });
});

var app = builder.Build();

// -----------------------------------------------------------
// PIPELINE CONFIGURATION
// -----------------------------------------------------------

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseCors();
app.UseHttpsRedirection();
app.UseAuthorization();
app.MapControllers();

Console.WriteLine("🚀 Green Pulse Backend is running!");
Console.WriteLine("📡 MQTT Services: MqttSubscriberService, WateringService");
Console.WriteLine($"🔥 Firestore Project: {projectId}");

app.Run();