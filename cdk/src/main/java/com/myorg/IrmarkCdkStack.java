package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.Port;

import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDrivers;
import software.amazon.awscdk.services.ecs.PortMapping;

import java.util.List;

public class IrmarkCdkStack extends Stack {

    public IrmarkCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public IrmarkCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // ----------------------------------------------------------
        // 1. VPC + ECS Cluster
        // ----------------------------------------------------------
        Vpc vpc = Vpc.Builder.create(this, "IRMarkVpc")
                .maxAzs(2)
                .build();

        Cluster cluster = Cluster.Builder.create(this, "IRMarkCluster")
                .vpc(vpc)
                .build();

        // ----------------------------------------------------------
        // 2. Docker Image: Build from /app (new structure)
        // ----------------------------------------------------------
        // Path is relative to /cdk folder
        String dockerContextPath = "../app";

        ContainerImage appImage = ContainerImage.fromAsset(dockerContextPath);

        // ----------------------------------------------------------
        // 3. Task Definition
        // ----------------------------------------------------------
        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "IRMarkTaskDefinition")
                .cpu(1024)
                .memoryLimitMiB(2048)
                .build();

        taskDef.addContainer("AppContainer",
                ContainerDefinitionOptions.builder()
                        .image(appImage)
                        .logging(LogDrivers.awsLogs(
                                AwsLogDriverProps.builder()
                                        .streamPrefix("IRMarkApp")
                                        .build()
                        ))
                        .portMappings(List.of(
                                PortMapping.builder()
                                        .containerPort(8080)
                                        .build()
                        ))
                        .build()
        );

        // ----------------------------------------------------------
        // 4. Fargate Service
        // ----------------------------------------------------------
        FargateService service = FargateService.Builder.create(this, "IRMarkFargateService")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .assignPublicIp(true)
                .build();

        // Allow public HTTP access to port 8080
        service.getConnections().allowFromAnyIpv4(
                Port.tcp(8080),
                "Allow inbound traffic to Spring Boot application"
        );

        // ----------------------------------------------------------
        // 5. Outputs
        // ----------------------------------------------------------
        new CfnOutput(this, "SecurityGroupId",
                CfnOutputProps.builder()
                        .value(service.getConnections().getSecurityGroups().get(0).getSecurityGroupId())
                        .build()
        );

        new CfnOutput(this, "Info",
                CfnOutputProps.builder()
                        .value("Service deployed. Get Public IP from ECS task → Public IP → port 8080.")
                        .build()
        );
    }
}
