Zoom Dashboard Backend – Setup Guide
 Install Requirements

Java 17

Maven


Frontend Connection

Inside SimpleController.java update:
@CrossOrigin(origins = "http://localhost:5173")



 Set the Backend Port

Inside application.properties, we must set:
server.port=8085



Run Backend

Open terminal in this folder:
mvn spring-boot:run



“Before running the backend, add your Zoom API credentials (client_id, client_secret, account_id, and r) inside src/main/resources/application.properties.”


src/
 └── main/
      ├── java/com/zoomdash
      │     ├── SimpleController.java
      │     ├── ZoomService.java
      │     ├── ZoomMeeting.java
      │     ├── Participant.java
      │     ├── ZoomWebinarsResponse.java
      │     └── (all other model classes)
      └── resources/application.properties

pom.xml
README.md



