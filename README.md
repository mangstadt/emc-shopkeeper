EMC Shopkeeper is a Java Swing application that allows [Empire Minecraft][1] players to track their in-game shop sales.

For more information, see the project's [Github.io page][6].

# Build instructions

Requires:

* Java 8 or higher
* Maven

1. Install the "lib/microba-0.4.4.3.jar" library into your local Maven repository.  This must be done because this library does not exist on Maven Central:  
`mvn install:install-file \`  
`-Dfile=lib/microba-0.4.4.3.jar \`  
`-DgroupId=microba \`  
`-DartifactId=microba \`  
`-Dversion=0.4.4.3 \`  
`-Dpackaging=jar`
1. Create the fat JAR by running:    
`mvn clean compile assembly:single`

# Command-line interface

EMC Shopkeeper includes a limited command-line interface:

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
      All data:               --query
      Today's data:           --query="today"
      Data since last update: --query="since last update"
      Three days of data:     --query="2013-03-07 to 2013-03-09"
      Data up to today:       --query="2013-03-07 to today"
    --format=TABLE|CSV|BBCODE
      Specifies how to render the queried transaction data (defaults to TABLE).

    --export=QUERY
      Outputs every transaction in chronological order in CSV format.  Examples:
      All data:               --export
      Today's data:           --export="today"
      Data since last update: --export="since last update"
      Three days of data:     --export="2013-03-07 to 2013-03-09"
      Data up to today:       --export="2013-03-07 to today"

    --version
      Prints the version of this program.

    --help
      Prints this help message.

This program is a fan creation and is not affiliated with Minecraft (copyright Mojang) or Empire Minecraft (copyright Starlis).

[1]: http://empireminecraft.com
[2]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper.jnlp
[3]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper-full.jar
[4]: https://github.com/mangstadt/emc-shopkeeper/tree/master/screenshots
[5]: http://empireminecraft.com/threads/shop-statistics.22507/
[6]: http://mangstadt.github.io/emc-shopkeeper
