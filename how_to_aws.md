## RS-FISH : Distributed processing using AWS 
### How to:
Before starting, You need to have AWS user account with full access permission to:
- S3: to manage buckets and data.
- EMR: Elastic MapReduce, to create processing nodes and cluster. 
- EC2: Processing instances, used for parallel processing 
- IAM: to create identity access with limited permissions for tasks.

### Steps:
#### 1- Create Access User with S3 full access permission :
- Create new IAM group for RS-FISH project:
    - Click **Create new Group** from _**IAM dashboard**_ -> _**Groups**_ .
    - Add policy `AmazonS3FullAccess` to the group and confirm.
- Create new IAM user:  
    - **Add user** from _**IAM dashboard**_ -> _**Users**_ . 
    - Add the user to group previously created for the project.
    - Keep the **Public key** and **private key** , will be needed.
    
#### 3- Create EMR server
Click on **Create cluster** from EMR dashboard

##### _**Configuration**_: _Add Spark step for RS-Fish_
+ **_Launch mode_** :  select `Step execution` -> a new '_Add step menu_' will appear 
+ **_Step Type_** : select Spark `Application` and click **configure**
  + Spark-submit options: `--class net.preibisch.rsfish.spark.aws.AWSSparkRSFISH`
  + Application location*: `s3://preibischlab-release-jars/RS-Fish-jar-with-dependencies.jar`
  + Arguments: minimum task arguments for default params
  

      -i, --image=<image>       N5 container path in s3, e.g. -i s3://bucket-name/smFish.n5
      -o, --output=<output>     output CSV file in S3, e.g. -o s3://rs-fish/embryo_5_ch0.csv
      -p, --path=<path>         The path of the input Data inside bucket e.g. -p N2-702-ch0/c0/s0 
      -pk, --publicKey=<credPublicKey> User public key  (*previously created)
      -pp, --privateKey=<credPrivateKey> User private key

+ 
  + Action on failure: `Terminate cluster`
  + click `Add`
+ **_Relese_** : emr-5.33.0
+ Select hardware configuration and number of execution nodes.

You can optimize cost and gain up to **90%** by selecting **Spot** instances instead of on-demand instance,
When creating click on `advance mode` and select Spot instances for execution nodes.