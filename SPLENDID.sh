# !/bin/sh

# USAGE: SPLENDID.sh <config> <query>
#mainclass=de.uni_koblenz.west.evaluation.QueryProcessingEval
#mainclassfile=src/de/uni_koblenz/west/evaluation/QueryProcessingEval.java

# USAGE: SPLENDID.sh <config>
# EXAMPLE: SPLENDID.sh eval/config.properties
mainclass1=de.uni_koblenz.west.evaluation.SourceSelectionEval
mainclassfile1=src/de/uni_koblenz/west/evaluation/SourceSelectionEval.java

# USAGE: SPLENDID.sh <config> <query>
mainclass2=de.uni_koblenz.west.splendid.SPLENDID
mainclassfile2=src/de/uni_koblenz/west/splendid/SPLENDID.java

firstserviceclassfile=src/de/uni_koblenz/west/splendid/config/VoidRepositoryFactory.java
secondserviceclassfile=src/de/uni_koblenz/west/splendid/config/FederationSailFactory.java

# set classpath
classpath=./src:./resources

config=$1
properties=$2
timeout=$3
resultFile=$4
provenanceFile=$5
explainFile=$6
statFile=$7
query=$8

# include all jar files in classpath
# for jar in lib/*.jar; do classpath=$classpath:$jar; done

if [ $9 = true ]; then
    # # build SourceSelectionEval
    # javac -d ./bin -cp $classpath $mainclassfile1 $firstserviceclassfile $secondserviceclassfile
    # # build SPLENDID
    # javac -d ./bin -cp $classpath $mainclassfile2 $firstserviceclassfile $secondserviceclassfile
    mvn clean && mvn install dependency:copy-dependencies package
fi

if [ $10 = true ]; then
    # run SourceSelectionEval
    #echo "java -cp $classpath:./bin de.uni_koblenz.west.evaluation.SourceSelectionEval $properties $provenanceFile"
    #sourceSelectionTime=$(echo `java -cp $classpath:./bin de.uni_koblenz.west.evaluation.SourceSelectionEval $properties $provenanceFile`)
    sourceSelectionTimeAddFile="$(dirname $resultFile)/source_selection_time_add.txt"
    sstime=$(mvn -q exec:java -Dexec.mainClass="de.uni_koblenz.west.evaluation.SourceSelectionEval" -Dexec.args="$properties $provenanceFile")
    status=$?
    if [ $status -eq 0 ]; then
        echo "$sstime" > $sourceSelectionTimeAddFile
    else
        exit $status
    fi
    # sourceSelectionTime=$(echo `mvn -q exec:java -Dexec.mainClass="de.uni_koblenz.west.evaluation.SourceSelectionEval" -Dexec.args="$properties $provenanceFile"`)
fi

if [ $11 = true ]; then
    # run SPLENDID
    #java -cp $classpath:./bin $mainclass2 $1 $sourceSelectionTime $3 $4 $6 $7 $8
    echo "$config $sourceSelectionTime $timeout $resultFile $explainFile $statFile $query"
    mvn -q exec:java -Dexec.mainClass="de.uni_koblenz.west.splendid.SPLENDID" -Dexec.args="$config $timeout $resultFile $explainFile $statFile $query"
    status=$?
    exit $status;
fi