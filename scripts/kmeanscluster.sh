#!/bin/bash

if [ $# -lt 1 ]
then
	echo "Usage: $0 <num clusters>"
	exit
fi

BASEDIR=/home/gangulyd/research/fastclustering/
DATADIR=$BASEDIR/trecmblog-index/
SRCDIR=$BASEDIR/LuceneClusterer/src/
RESDIR=$BASEDIR/clustering-outputs/
SCRIPTDIR=$BASEDIR/scripts/
BUILDDIR=$BASEDIR/LuceneClusterer/build/classes/

NUMCLUSTERS=$1
CLUSTER_METHOD=kmeans

PROPFILE=$SCRIPTDIR/init.tweets.properties
LOGFILE=$BASEDIR/logs/${CLUSTER_METHOD}.${NUMCLUSTERS}.cluster.tweets.log

MAXITER=5

cd $SCRIPTDIR
cat > $PROPFILE  << EOF1

index=$DATADIR
#set the num clusters to be equal to the num queries for TREC Mblog task
numclusters=$NUMCLUSTERS

maxiters=$MAXITER
stopthreshold=0.1
eval=false
eval.numclasses=49
cluster.idfile=$RESDIR/trecmblog.${CLUSTER_METHOD}.${NUMCLUSTERS}.txt

id.field_name=url
content.field_name=words
ref.field_name=none

termsel.ratio=1
qrels=$SRCDIR/qrels.microblog2011-2012.txt

EOF1

cd $BUILDDIR

CP=.
for jarfile in `find $BASEDIR/LuceneClusterer/lib/ -name "*.jar"`
do
	CP=$CP:$jarfile
done

now=$(date +"%T")
echo "Program starting at $now"
$JAVA_HOME/bin/java -Xmx8G -cp $CP clusterer.KMeansClusterer $PROPFILE > $LOGFILE 2>&1

now=$(date +"%T")
echo "Program finished at $now"
