pipeline {
    agent any
    stages {
        stage('Initialization') {
            steps {
                echo 'Cloning from Github ....'
                script {
                    git credentialsId: 'githubshailesh', url: 'https://github.com/githubshailesh/apigeetest.git'
                    sh "git checkout master"
                    sh "ls -lrta"
                    sh 'tar xzf istio-1.7.6-asm.1-linux-amd64.tar.gz'
                    sh "ls -lrta"
                    sh 'cd istio-1.7.6-asm.1\n' +
                        'ls -lrta \n' +
                        'export PATH=$PWD/bin:$PATH \n' +
                        'echo $PATH'
                    sh 'echo $PATH'
                }
                // sh "az aks install-cli"
            }
        }

        stage('AZURE Login') {
            steps {
                script {
                    RESOURCE_GROUP="rg-kp-apigee-sandbox-01"
                    ASM_VERSION="1.7.6-asm.1"
                    CLUSTER_NAME="apigee-hybrid-acn-sb02"

                    withCredentials([azureServicePrincipal('f5a0d7e4-16e0-4d7f-81ab-52b8d00c15dc')]) {
                        sh 'az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET -t $AZURE_TENANT_ID'
                        sh 'az account set -s $AZURE_SUBSCRIPTION_ID'
                    }

                    sh 'helm version'
                    println "Resource Group is $RESOURCE_GROUP"
                    println "ASM Version is $ASM_VERSION"

                    sh 'az aks get-credentials --resource-group ' + RESOURCE_GROUP + '--name '+ CLUSTER_NAME

                }
            }
        }

        stage('Install Certificate Manager') {
            steps {
                sh 'kubectl apply --validate=false -f cert-manager.yaml'
            }
        }

        stage('Setup Cluster Public IP') {
            steps {
                script{
                    statusCode = sh(script:'az network public-ip show --resource-group rg-kp-apigee-sandbox-01 --name apigee-hybrid-acn-sb01-public-ip-sjv --query ipAddress --output tsv', returnStatus:true)
                    echo "${statusCode}"
                    if (statusCode == 0 ){
                        publicIP = sh(script:'az network public-ip show --resource-group rg-kp-apigee-sandbox-01 --name apigee-hybrid-acn-sb01-public-ip-sjv --query ipAddress --output tsv', returnStdout:true)    
                        println "Existing public IP is $publicIP"
                    }
                    else {
                        publicIP = sh(script:'az network public-ip create --resource-group rg-kp-apigee-sandbox-01 --name apigee-hybrid-acn-sb01-public-ip-sjv --location westus2 --sku Standard --allocation-method static --query publicIp.ipAddress -o tsv', returnStdout:true)
                        println "New Public IP is $publicIP"
                    }
                }
            }
        }

        stage('Setup Namespace') {
            steps {
                sh 'kubectl create namespace istio-system'
            }
        }
        
        stage('Setup Istio-Operator') {
            steps {
                script{
                    sh 'helm template istio.operator.example --set tag=1.7.6-asm.1,serviceAnnotations=rg-kp-apigee-sandbox-01,LBIp=' + publicIP  + '> manifest-istio-operator.yaml'
                    sh 'ls -lart'

                    sh 'istioctl install -f manifest-istio-operator.yaml'
                }
            }
        }
        // sh 'kubectl delete -f cert-manager.yaml'
    }
}
