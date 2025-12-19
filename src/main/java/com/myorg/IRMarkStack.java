package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.*;

public class IRMarkStack extends Stack {
    // ... (Constructors remain the same) ...

    public IRMarkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // 1. VPC and Cluster (Standard Setup)
        Vpc vpc = Vpc.Builder.create(this, "IRMarkVpc")
                .maxAzs(2)
                .build();

        Cluster cluster = Cluster.Builder.create(this, "IRMarkCluster")
                .vpc(vpc)
                .build();
       
        // 2. BUILD DOCKER IMAGE FROM LOCAL ASSET
        // The path is relative to the CDK project root.
        // CDK will find the Dockerfile in this directory, build the image, and push to a temporary ECR repo.
        ContainerImage appImage = ContainerImage.fromAsset(
            "../paycaptain-ir-mark-calculator" // <-- Correct path to the folder containing Dockerfile
        );
        
        // 3. Fargate Task Definition
        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "IRMarkTaskDefinition")
                .cpu(1024)
                .memoryLimitMiB(2048)
                .build();

        taskDef.addContainer("AppContainer", ContainerDefinitionOptions.builder()
                .image(appImage) // <-- Use the image built from the local asset
                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                        .streamPrefix("SpringBoot")
                        .build()))
                .portMappings(java.util.List.of(PortMapping.builder()
                        .containerPort(8080)
                        .build()))
                .build());

        // // 4. Load Balancer
        // ApplicationLoadBalancer lb = ApplicationLoadBalancer.Builder.create(this, "LB")
        //         .vpc(vpc)
        //         .internetFacing(true)
        //         .build();

        // ApplicationListener listener = lb.addListener("Listener", BaseApplicationListenerProps.builder()
        //         .port(80)
        //         .build());

        // 5. Fargate Service
        FargateService service = FargateService.Builder.create(this, "FargateService")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .assignPublicIp(true) 
                .build();

        // 5. Update Security Group 🚨
        // Add an Ingress rule to allow inbound traffic on port 8080 from the internet (0.0.0.0/0)
        // This is necessary because the ALB is no longer managing the public access.
        service.getConnections().allowFromAnyIpv4(
                software.amazon.awscdk.services.ec2.Port.tcp(8080), 
                "Allow inbound traffic on the application port"
        );

        // 6. Listener Targets and Health Check
        // listener.addTargets("AppTarget", AddApplicationTargetsProps.builder()
        //         .port(8080)
        //         .targets(java.util.List.of(service))
        //         .healthCheck(HealthCheck.builder()
        //                 .healthyThresholdCount(2)
        //                 .unhealthyThresholdCount(5)
        //                 .interval(Duration.seconds(30))
        //                 .timeout(Duration.seconds(5))
        //                 .path("/")
        //                 .port("8080")
        //                 .build())
        //         .build());

        // // Output the Load Balancer URL
        // new CfnOutput(this, "LoadBalancerURL",
        //         CfnOutputProps.builder()
        //                 .value("http://" + lb.getLoadBalancerDnsName())
        //                 .build());

        // 6. Output (Placeholder for the Public IP)
        // NOTE: The actual public IP is not known at *deployment time*.
        // The value below is a simple placeholder to guide the user.
        new CfnOutput(this, "TaskAccessNote",
                CfnOutputProps.builder()
                        .value("Access is via the task's public IP on port 8080. The IP changes when the task restarts. You must check the ECS Console to get the current IP.")
                        .build());
                        
        new CfnOutput(this, "SecurityGroup",
                CfnOutputProps.builder()
                        .value(service.getConnections().getSecurityGroups().get(0).getSecurityGroupId())
                        .build());
    }
}