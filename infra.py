"""
    python infra.py up       # deploy everything
    python infra.py down     # tear everything down
"""

import os
import sys
import subprocess
import aws_cdk as cdk
from aws_cdk import (
    Stack, CfnOutput, RemovalPolicy,
    aws_ec2 as ec2,
    aws_ecs as ecs,
    aws_ecr as ecr,
    aws_apigatewayv2 as apigwv2,
    aws_logs as logs,
    aws_servicediscovery as sd,
)
from aws_cdk.aws_apigatewayv2_integrations import HttpServiceDiscoveryIntegration
from aws_cdk.aws_apigatewayv2_authorizers import HttpJwtAuthorizer
from constructs import Construct

# ======================= CONFIGURE THESE =======================
ECR_REPO_NAME    = "oomr/filestorage"
IMAGE_TAG        = "latest"
CONTAINER_PORT   = 8080
REGION           = "us-west-1"
VPC_ID           = "EMPTY"

COGNITO_USER_POOL_ID = "EMPTY"         
COGNITO_APP_CLIENT_ID = "EMPTY" 
# ===============================================================

COGNITO_ISSUER = f"https://cognito-idp.{REGION}.amazonaws.com/{COGNITO_USER_POOL_ID}"
STACK_NAME = "FilestorageStack"



class AppStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # --- existing VPC (same one as my RDS VPC) ---
        vpc = ec2.Vpc.from_lookup(self, "Vpc", vpc_id=VPC_ID)

        # --- ECS ---
        cluster = ecs.Cluster(self, "Cluster", vpc=vpc)
        namespace = cluster.add_default_cloud_map_namespace(name="app.local")

        repo = ecr.Repository.from_repository_name(self, "Repo", ECR_REPO_NAME)

        task_def = ecs.FargateTaskDefinition(self, "Task", cpu=256, memory_limit_mib=512)
        task_def.add_container("App",
            image=ecs.ContainerImage.from_ecr_repository(repo, tag=IMAGE_TAG),
            port_mappings=[ecs.PortMapping(container_port=CONTAINER_PORT)],
            environment={
                "POSTGRES_USERNAME":"EMPTY",
                "POSTGRES_PASSWORD":"EMPTY",
                "DB_HOST":"EMPTY",
                "AWS_ACCESS_KEY_ID": "EMPTY",
                "AWS_SECRET_ACCESS_KEY":"EMPTY"
            },
        )


        ecs_sg = ec2.SecurityGroup(self, "EcsSg", vpc=vpc)

        # Allow ECS tasks to reach my existing RDS on port 5432
        rds_sg = ec2.SecurityGroup.from_security_group_id(self, "RdsSg", "sg-0251b2feb410342f6")
        rds_sg.add_ingress_rule(
            peer=ecs_sg,
            connection=ec2.Port.tcp(5432),
            description="ECS to RDS",
        )

        service = ecs.FargateService(self, "Svc",
            cluster=cluster,
            task_definition=task_def,
            desired_count=1,
            assign_public_ip=True,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
            security_groups=[ecs_sg],
            cloud_map_options=ecs.CloudMapOptions(
                name="api",
                cloud_map_namespace=namespace,
                dns_record_type=sd.DnsRecordType.SRV,
            ),
        )

        # --- API Gateway ---
        vpc_link = apigwv2.VpcLink(self, "VpcLink", vpc=vpc, subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC))

        ecs_sg.add_ingress_rule(
            peer=ec2.Peer.ipv4(vpc.vpc_cidr_block),
            connection=ec2.Port.tcp(CONTAINER_PORT),
            description="API GW VPC Link",
        )

        integration = HttpServiceDiscoveryIntegration("Ecs",
            service=service.cloud_map_service,
            vpc_link=vpc_link,
        )

        # Cognito JWT authorizer
        authorizer = HttpJwtAuthorizer("CognitoAuth",
            jwt_issuer=COGNITO_ISSUER,
            jwt_audience=[COGNITO_APP_CLIENT_ID],
            identity_source=["$request.header.Authorization"],
        )

        http_api = apigwv2.HttpApi(self, "Api",
            api_name=f"{construct_id}-api",
            cors_preflight=apigwv2.CorsPreflightOptions(
                allow_origins=["*"],
                allow_methods=[apigwv2.CorsHttpMethod.ANY],
                allow_headers=["Authorization", "Content-Type", "X-User-Id"],
            ),
        )

        # Catch-all route with auth
        api_methods = [
            apigwv2.HttpMethod.GET,
            apigwv2.HttpMethod.POST,
            apigwv2.HttpMethod.PUT,
            apigwv2.HttpMethod.PATCH,
            apigwv2.HttpMethod.DELETE,
            apigwv2.HttpMethod.HEAD,
        ]
        http_api.add_routes(
            path="/{proxy+}",
            methods=api_methods,
            integration=integration,
            authorizer=authorizer,
        )
        http_api.add_routes(
            path="/",
            methods=api_methods,
            integration=integration,
            authorizer=authorizer,
        )

        CfnOutput(self, "ApiUrl", value=http_api.url or "")


# ======================= CLI =======================
def main():
    action = sys.argv[1]

    # Write a temp cdk.json so CDK knows how to run this file
    cdk_json = '{"app": "python3 infra.py _cdk"}'
    with open("cdk.json", "w") as f:
        f.write(cdk_json)

    cmds = {
        "up":    ["cdk", "deploy", STACK_NAME, "--require-approval", "never", "--outputs-file", "cdk-outputs.json"],
        "down":  ["cdk", "destroy", STACK_NAME, "--force"],
        "synth": ["cdk", "synth", STACK_NAME],
    }

    subprocess.run(cmds[action], check=True)

    if action == "up":
        import json
        try:
            with open("cdk-outputs.json") as f:
                outputs = json.load(f)
            api_url = outputs[STACK_NAME]["ApiUrl"]
            print(f"\nDeployed! API Gateway URL:\n\n   {api_url}\n")
        except (FileNotFoundError, KeyError):
            print("\nDeployed! Double Check the CloudFormation outputs for API URL.")


if "_cdk" in sys.argv or "synth" in str(sys.argv):
    app = cdk.App()
    AppStack(app, STACK_NAME, env=cdk.Environment(
        account=os.environ.get("CDK_DEFAULT_ACCOUNT"),
        region=os.environ.get("CDK_DEFAULT_REGION", REGION),
    ))
    app.synth()
elif __name__ == "__main__":
    main()
