# RS-FISH-Spark
The Spark version of RS-FISH can analyze large N5 volumes in a distributed fashion locally, on the cluster, or in the cloud. 

This repository contains code to run https://github.com/PreibischLab/RS-FISH in blocks using Spark. It requires the dataset to be saved in the N5 format. You can easily resave and open an N5 dataset in Fiji using the pre-installed [n5-ij](https://github.com/saalfeldlab/n5-ij). An example N5 file can be found in the [resource folder](https://github.com/PreibischLab/RS-FISH-Spark/tree/main/src/main/resources).

The code will virtually split the task into small, overlapping blocks and compute the localizations independently for each block and later merge them into a complete list of points for the entire image. The result is identical to running it on one big block (which is what the plugin does).

The code to run the distributed, [Spark](http://spark.apache.org)-based RS-FISH implementation on image volumes can be found in the [RS-FISH class](https://github.com/PreibischLab/RS-FISH-Spark/blob/main/src/main/java/net/preibisch/rsfish/spark/SparkRSFISH.java).



### Local execution <a name="local">
</a> 

For **local execution** (comparable to efficient multi-threading), compile the repository using `mvn clean package` and execute the above class or import the project into Eclipse and run it from there. Importantly, Spark requires the JVM option `-Dspark.master=local[8]`, where the number in brackets specifies the number of threads being used.



### Cluster execution <a name="cluster">
</a> 

For **cluster execution** please contact your IT department for how to distribute Spark code on your local cluster. You will need to do create a **fatjar** using Maven first.



### Cloud execution <a name="cloud">
</a> 

For **cloud execution**: Distributed processing using Amazon Web Services (AWS). 

Using cloud services enables scaling analysis of terabyte-sized data or thousands of images regardless of hardware devices. Within the RS-FISH-Cloud version, terabytes of images can be processed within one hour using AWS services.

**How to:**

Running RS-FISH in AWS requires a user account with four basic permissions:
* S3: is a data storage service used to store and manage input and output data.
* IAM: is Identity and Access Management, and it enables you to manage access to AWS services and resources securely.
* EC2: used to rent virtual computers on which to run our application. 
* EMR: Elastic MapReduce used to create, manage and scale EC2 instances.

**Steps:**
_**1. Create an Access User with S3 full access permission:**_

Create a new IAM group for the RS-FISH project:


Go to the IAM dashboard:

Click Create new Group from ```IAM dashboard -> Groups```.


Add the policy AmazonS3FullAccess to the group and confirm.


Create a new IAM user:
Add user from the ```IAM dashboard -> Users```.


Add the user to the group previously created for the project.


Save and keep the credentials (Public key & private key). It will be needed for running the project.


_**2. Upload your data:**_

It is recommended to create a new S3 bucket for the project. 
Then upload the input data into the bucket.
This can be done using the AWS website or using our java application.

_**3. Create an EMR server**_ 

To allocate the needed resources to process our data, an EMR cluster is needed:
Click on Create cluster from the EMR dashboard.

*Configuration:* 
* Add Spark step for RS-FISH:
* Release : emr-5.33.0
* Launch mode: select ```Step execution -> a new 'Add step menu'``` will appear
* Step Type: select Spark Application and click configure

* Spark-submit options: --class 
* for N5 input: net.preibisch.rsfish.spark.aws.AWSSparkRSFISH
* For multiple tifs: net.preibisch.rsfish.spark.aws.RunAWSSparkRSFISHIJ
* Application location*: s3://preibischlab-release-jars/RS-Fish-jar-with-dependencies.jar
* Arguments: minimum task arguments for default params:
-i =< N5 container path in s3, e.g. -i s3://bucket-name/smFish.n5 -o, --output= output CSV file in S3, e.g. -o s3://rs-fish/embryo_5_ch0.csv -p, --path= The path of the input Data inside bucket e.g. -p N2-702-ch0/c0/s0 -pk, --publicKey= User public key (*previously created) -pp, --privateKey= User private key
 
Action on failure: Terminate cluster

Click Add
 

Select the hardware configuration and the number of execution nodes.



**Optimization:**

_**Low cost:**_

Cost can be optimized up to 90% by selecting Spot instances instead of on-demand instances. When creating your project click on advance mode and select Spot instances for the execution nodes.
E.g., we used instance: r4.2xlange for spot price $0.181/hr where the on-demand price is  $0.640/hr


_**High memory instances:**_

AWS provides three categories of instances: We prefer R-type instances over the other instance types for memory-intensive applications. For compute-intensive applications, we prefer C-type instances. For applications balanced between memory and compute, we prefer M-type general-purpose instances.
In our case, we need R instances r5.xlarge 4vCPU 32Gib memory.

_**Budget information:**_

For 10000 images processed in 1h 9min using 39 execution instances with a total cost:
Spot: 8,46$
On-demand: 28,16$ 
To keep the budget under control, you can use the budget service provided by AWS,
it allows you to get email alerts based on current or forecasted costs.
 
