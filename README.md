# TFTP-ServerClient
A Java-based implementation of an extended TFTP protocol with a thread-per-client server and a multithreaded client. The project supports bi-directional messaging, file transfer operations, and error handling with an encoded binary protocol. Maven is used for build automation, ensuring modular and scalable development.

-Clone the Repository
-This project uses Maven for build automation. To compile the project, run: mvn clean install.
-Project Structure:
      src/main/java: Contains the main application code.
      server: Code for the TFTP server, including the thread-per-client model.
      client: Code for the multithreaded TFTP client.
      protocol: Implementation of the extended TFTP protocol (request and response handling).
      src/main/resources: Configuration files or additional resources.
      src/test/java: Test cases for the server and client.
- Run server: java -jar target/server.jar
- Run client: java -jar target/client.jar
- Run commands s.a.:
      RRQ <filename>: Request to read a file from the server.
      WRQ <filename>: Write a file to the server.
      DIR: List all files on the server.
      DISC: Disconnect from the server.
