# RS-FISH-Spark
Spark version of RS-FISH that can analyze large N5 volumes in a distributed fashion locally, on the cluster or in the cloud

This repository contains code to run https://github.com/PreibischLab/RS-FISH in blocks using Spark. It requires the dataset to be saved in the N5 format. You can easily resave and open an N5 dataset in Fiji using the pre-installed [n5-ij](https://github.com/saalfeldlab/n5-ij). An example N5 can be found in the 

The code will virtually split the task into small, overlapping blocks and compute the localizations independently for each block and later merge them into a complete list of points for the entire image. The result is identical to running it on one big block (which is what the plugin does).

The code to run the distributed, [Spark](http://spark.apache.org)-based RS-FISH implementation on image volumes is found in the [RS-FISH class](https://github.com/PreibischLab/RS-FISH-Spark/blob/main/src/main/java/net/preibisch/rsfish/spark/SparkRSFISH.java).

For **local execution** (comparable to efficient multi-threading), compile the repository using `mvn clean package` and execute the above class or import the project into Eclipse and run it from there. Importantly, Spark requires the JVM option `-Dspark.master=local[8]`, where the number in brackets specifies the number of threads being used.

For **cluster execution** please contact your IT department for how to distribute Spark code on your local cluster. You will need to do create a **fatjar** using Maven first.

For **cloud execution** ... @mzouink could you maybe give this a shot?
