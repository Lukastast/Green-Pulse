#FROM eclipse-mosquitto:latest

# Copy configs
#COPY Broker/mosquitto.conf /mosquitto/config/mosquitto.conf
#COPY Broker/acl.conf /mosquitto/config/acl.conf
#COPY Broker/passwd /mosquitto/config/passwd
#COPY certs/server.crt /mosquitto/config/server.crt
#COPY certs/server.key /mosquitto/config/server.key

# Secure permissions (fixes default allow)
#RUN chmod 0600 /mosquitto/config/passwd
#RUN chown mosquitto:mosquitto /mosquitto/config/passwd
#RUN chmod 0600 /mosquitto/config/acl.conf
#RUN chown -R mosquitto:mosquitto /mosquitto/config

#EXPOSE 8883