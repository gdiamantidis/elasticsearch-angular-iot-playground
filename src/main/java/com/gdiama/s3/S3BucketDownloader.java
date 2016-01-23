package com.gdiama.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;

import java.io.File;

public class S3BucketDownloader {

    public static final String BUCKET = "connectedboiler-data-intfuture";
    public static final String DESTINATION = "/Users/gdiama/Desktop/connectedboilerdata-intfuture-2/compressed/";
    private AmazonS3 s3client;

    public static void main(String[] args) {
        S3BucketDownloader s3DataDownloader = new S3BucketDownloader();
        s3DataDownloader.downloadBucket(BUCKET);
    }

    public void downloadBucket(String bucketName) {
        s3client = new AmazonS3Client(new ProfileCredentialsProvider());


        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName);
        ObjectListing objectListing;

        do {
            objectListing = s3client.listObjects(listObjectsRequest);
            objectListing.getObjectSummaries().stream().forEach(this::download);
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
    }

    private void download(S3ObjectSummary objectSummary) {
        System.out.println(objectSummary.getKey());
        try {
             s3client.getObject(
                    new GetObjectRequest(BUCKET, objectSummary.getKey()),
                    new File(DESTINATION + objectSummary.getKey().split("/")[1]));
        } catch (AmazonClientException e) {
            e.printStackTrace();
        }
    }
}
