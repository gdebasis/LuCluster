#!/bin/bash

if [ $# -lt 1 ]
then
	echo "Usage: $0 <cluster o/p file>"
	exit
fi

BASEDIR=/home/gangulyd/research/fastclustering/
DATADIR=$BASEDIR/trecmblog-index/
SRCDIR=$BASEDIR/LuceneClusterer/src/
RESDIR=$BASEDIR/clustering-outputs/
SCRIPTDIR=$BASEDIR/scripts/
BUILDDIR=$BASEDIR/LuceneClusterer/build/classes/

OPFILE=$1
PROPFILE=$SCRIPTDIR/init.tweets.${NUMCLUSTERS}.properties

cd $SCRIPTDIR
cat > $PROPFILE  << EOF1

index=$DATADIR
#set the num clusters to be equal to the num queries for TREC Mblog task
numclusters=$NUMCLUSTERS

maxiters=5
stopthreshold=0.1
eval=false
cluster.idfile=$OPFILE

id.field_name=url
content.field_name=words
ref.field_name=none

termsel.ratio=1
qrels=$BASEDIR/qrels.microblog2011-2012.txt

EOF1

cd $BUILDDIR

CP=.
for jarfile in `find $BASEDIR/LuceneClusterer/lib/ -name "*.jar"`
do
	CP=$CP:$jarfile
done

$JAVA_HOME/bin/java -Xmx8G -cp $CP clusterer.QrelClusterEvaluator $PROPFILE

