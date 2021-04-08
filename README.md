# RS-FISH-Spark
Spark version of RS-FISH that can analyze large N5 volumes in a distributed fashion locally, on the cluster or in the cloud

This repository contains code to run https://github.com/PreibischLab/RS-FISH in blocks using Spark. It requires the dataset to be saved in the N5 format. You can easily resave and open an N5 dataset in Fiji using the pre-installed [n5-ij](https://github.com/saalfeldlab/n5-ij).

The code to run the distributed, [Spark](http://spark.apache.org)-based RS-FISH implementation on image volumes is found in the [RS-FISH class](https://github.com/PreibischLab/RS-FISH-Spark/blob/main/src/main/java/net/preibisch/rsfish/spark/SparkRSFISH.java). 
