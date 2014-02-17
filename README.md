#Overview

EMC Shopkeeper is a program that allows you to download and view your [Empire Minecraft][1] shop transactions.

Empire Minecraft forum thread: [http://empireminecraft.com/threads/shop-statistics.22507/][5]

See [screenshots][4].

#To Run

*Requires:* Java 6

There are two ways to run EMC Shopkeeper:

[Auto-runner][2] (Java WebStart)  
Automatically downloads EMC Shopkeeper and runs it.  The auto-runner ensures that you are always running the latest version.  If you are having trouble running it, try right-clicking on the .jnlp file and selecting "Open With > Java".  The auto-runner does not work on Mac computers.

[JAR file][3]  
Self-contained JAR file.  Works on all systems.  Double-click the file to run EMC Shopkeeper.  To run it on a Mac, right click on the file.  Then, hold down the "Control" key and click "Open". 

#To Build

*Requires:* Java 6, Maven

1. The "microba" library (located in the "lib" directory) does not exist in the central Maven repository, and must be added to your local repository:  
`mvn install:install-file \`  
`-Dfile=lib/microba-0.4.4.3.jar \`  
`-DgroupId=microba \`  
`-DartifactId=microba \`  
`-Dversion=0.4.4.3 \`  
`-Dpackaging=jar`
1. Build the project:  
`mvn clean compile assembly:single`

You can then execute the JAR in the "target" directory by double clicking on it or running this command:

`java -jar target/emc-shopkeeper-VERSION-SNAPSHOT-jar-with-dependencies.jar`.

#Command-line arguments

EMC Shopkeeper includes a command-line interface, which contains limited, but functional functionality.

    General arguments
    These arguments can be used for the GUI and CLI.
    ================================================
    --profile=PROFILE
      The profile to use (defaults to "default").

    --profile-dir=DIR
      The path to the directory that contains all the profiles
      (defaults to "USER_HOME/.emc-shopkeeper").

    --db=PATH
      Overrides the database location (stored in the profile by default).

    --log-level=FINEST|FINER|FINE|CONFIG|INFO|WARNING|SEVERE
      The log level to use (defaults to INFO).

    CLI arguments
    Using one of these arguments will launch EMC Shopkeeper in CLI mode.
    ================================================
    --update
      Updates the database with the latest transactions.
    --start-page=PAGE
      Specifies the transaction history page number to start at during
      the first update (defaults to 1).
    --stop-page=PAGE
      Specifies the transaction history page number to stop at during
      the first update (defaults to the last page).

    --query=QUERY
      Shows the net gains/losses of each item.  Examples:
      All data:           --query
      Today's data:       --query="today"
      Three days of data: --query="2013-03-07 to 2013-03-09"
      Data up to today:   --query="2013-03-07 to today"
    --format=TABLE|CSV|BBCODE
      Specifies how to render the queried transaction data (defaults to TABLE).

    --version
      Prints the version of this program.

    --help
      Prints this help message.

*Note: This program is a fan creation and is not affiliated with Minecraft (copyright Mojang) or Empire Minecraft (copyright Starlis).*

[1]: http://empireminecraft.com
[2]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper.jnlp
[3]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper-full.jar
[4]: https://github.com/mangstadt/emc-shopkeeper/tree/master/screenshots
[5]: http://empireminecraft.com/threads/shop-statistics.22507/