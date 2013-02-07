A utility for saving all your EMC shop transactions to a database.

#Setup Instructions

1. Login to empire.us and save all the cookies to: `~/.emc-shopkeeper/cookies.properties`.
1. Build the project: `mvn clean compile assembly:single`

#Usage

Executing the JAR will update the database with transactions that occurred after the last time you ran emc-shopkeeper.

`java -jar target/emc-shopkeeper-0.1-SNAPSHOT-jar-with-dependencies.jar`.

Transactions are saved to a Derby database, located at `~/.emc-shopkeeper/db/data`.