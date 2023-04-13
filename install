#!/bin/bash

# This script is shamelessly adapted from https://github.com/saalfeldlab/n5-utils, thanks @axtimwalde & co!

VERSION="0.0.1-SNAPSHOT"
INSTALL_DIR=${1:-$(pwd)}

echo ""
echo "Installing into $INSTALL_DIR"

# check for operating system
if [[ "$OSTYPE" == "linux-gnu" ]]; then
  echo "Assuming on Linux operating system"
  MEM=$(cat /proc/meminfo | grep MemTotal | sed s/^MemTotal:\\\s*\\\|\\\s\\+[^\\\s]*$//g)
  MEMGB=$(($MEM/1024/1024))
  MEM=$((($MEMGB/5)*4))
elif [[ "$OSTYPE" == "darwin"* ]]; then
  echo "Assuming on MacOS X operating system"
  # sysctl returns total hardware memory size in bytes
  MEM=$(sysctl hw.memsize | grep hw.memsize | sed s/hw.memsize://g)
  MEMGB=$(($MEM/1024/1024/1024))
  MEM=$((($MEMGB/5)*4))
else
  echo "ERROR - Operating system (arg2) must be either linux or osx - EXITING (on windows please run as a normal Java class from e.g. Eclipse)"
  exit
fi

echo "Available memory:" $MEMGB "GB, setting Java memory limit to" $MEM "GB"

mvn clean install
mvn -Dmdep.outputFile=cp.txt -Dmdep.includeScope=runtime dependency:build-classpath

echo ""
echo "Installing 'SparkRSFISH' command into" $INSTALL_DIR
echo ""

echo '#!/bin/bash' > rs-fish-spark
echo '' >> rs-fish-spark
echo "JAR=\$HOME/.m2/repository/net/preibisch/RS-FISH-Spark/${VERSION}/RS-FISH-Spark-${VERSION}.jar" >> rs-fish-spark
echo 'java \' >> rs-fish-spark
echo "  -Xmx${MEM}g \\" >> rs-fish-spark
echo '  -XX:+UseConcMarkSweepGC \' >> rs-fish-spark
echo -n '  -cp $JAR:' >> rs-fish-spark
echo -n $(cat cp.txt) >> rs-fish-spark
echo ' \' >> rs-fish-spark
echo '  net.preibisch.rsfish.spark.SparkRSFISH "$@"' >> rs-fish-spark

echo ""
echo "Installing 'SparkRSFISH_IJ' command into" $INSTALL_DIR
echo ""

echo '#!/bin/bash' > rs-fish-spark-ij
echo '' >> rs-fish-spark-ij
echo "JAR=\$HOME/.m2/repository/net/preibisch/RS-FISH-Spark/${VERSION}/RS-FISH-Spark-${VERSION}.jar" >> rs-fish-spark-ij
echo 'java \' >> rs-fish-spark-ij
echo "  -Xmx${MEM}g \\" >> rs-fish-spark-ij
echo '  -XX:+UseConcMarkSweepGC \' >> rs-fish-spark-ij
echo -n '  -cp $JAR:' >> rs-fish-spark-ij
echo -n $(cat cp.txt) >> rs-fish-spark-ij
echo ' \' >> rs-fish-spark-ij
echo '  net.preibisch.rsfish.spark.SparkRSFISH_IJ "$@"' >> rs-fish-spark-ij

chmod a+x rs-fish-spark
chmod a+x rs-fish-spark-ij

if [ $(pwd) == "$INSTALL_DIR" ]; then
    echo "Installation directory equals current directory, we are done."
else
	echo "Creating directory $INSTALL_DIR and moving files..."
    mkdir -p $INSTALL_DIR
    mv st-view $INSTALL_DIR/
fi

rm cp.txt

echo "Installation finished."