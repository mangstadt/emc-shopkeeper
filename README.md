#Overview

EMC Shopkeeper is a program that allows you to download and view your [Empire Minecraft][1] shop transactions.

See [screenshots][4].

#To Run

*Requires:* Java 6

[Click here][2] to run EMC Shopkeeper

(or you can [download the full JAR file][3])

#To Build

*Requires:* Java 6, Maven

1. Add the "microba" dependency to your local repository:  
`mvn install:install-file \`  
`-Dfile=lib/microba-0.4.4.3.jar \`  
`-DgroupId=microba \`  
`-DartifactId=microba \`  
`-Dversion=0.4.4.3 \`  
`-Dpackaging=jar`
1. Build the project:  
`mvn clean compile assembly:single`

You can then execute the JAR in the "target" directory by double clicking on it or running this command:

`java -jar target/emc-shopkeeper-0.1-SNAPSHOT-jar-with-dependencies.jar`.

#Command-line arguments

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

    --settings=PATH
      Overrides the settings file location (stored in the profile by default).

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

*This program is a fan creation and is not affiliated with Minecraft (copyright Mojang) or Empire Minecraft (copyright Kalland Labs).*

[1]: http://empireminecraft.com
[2]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper.jnlp
[3]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper-full.jar
[4]: https://github.com/mangstadt/emc-shopkeeper/tree/master/screenshots
