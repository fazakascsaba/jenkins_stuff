def my_work_folder="/weblogic_appdata/paymentsense/outgoing/statements"
def my_archive_folder="/weblogic_appdata/archive/paymentsense/outgoing/statements"
def begins_with="Statement_"
def ends_with="xml"
def file_prefix="Statement_All_XML"
def oos_destination="Output/Acquirer/Statement"


pipeline {
    agent { label 'main' }

    stages {
        stage('Create directories') {
            options {
                timeout(time: 1, unit: 'MINUTES') 
            }
            steps {
                sh '''
				mkdir -p $WORKSPACE/in
				mkdir -p $WORKSPACE/archive
				'''
            }
        }
		
        stage('start pack-files.sh on c01p1-sftp04') {
            steps {
                sh "ssh opc@c01p1-sftp04 sudo /home/opc/scripts/pack-files.sh ${my_work_folder} ${begins_with} ${ends_with} ${file_prefix}"
            }
        

        }
		
        stage('create enrcypt directory') {
            options {
                timeout(time: 1, unit: 'MINUTES') 
            }
            steps {
                sh "ssh weblogic@c01p1-sftp04 'mkdir -p ${my_work_folder}/tar_gz/encrypt'"
            }
        }

        stage('encrypt files') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'for i in \$(ls ${my_work_folder}/tar_gz/${file_prefix}*gz); do /usr/bin/gpg -v --output \$i.pgp --encrypt --recipient itteam@paymentsense.com --trust-model always \$i; done'"
            }
        }


        stage('move encrypted files to encrypt folder') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'if [ -n \"\$(ls -A ${my_work_folder}/tar_gz/*.pgp 2>/dev/null)\" ]; then mv ${my_work_folder}/tar_gz/*.pgp ${my_work_folder}/tar_gz/encrypt; else echo \"WARNING No files to encrypt.\"; fi'"
            }
        }

        stage('copy files to OOS') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'if [ -n \"\$(ls -A ${my_work_folder}/tar_gz/encrypt/*.pgp 2>/dev/null)\" ]; then oci os object bulk-upload -bn file-exchange --src-dir ${my_work_folder}/tar_gz/encrypt/ --no-overwrite --object-prefix ${oos_destination}/; else echo \"WARNING No files to upload.\"; fi'"
            }
        }

        stage('remove pgp file') {
            options {
                timeout(time: 1, unit: 'MINUTES') 
            }
            steps {
                sh "ssh weblogic@c01p1-sftp04 'rm -f ${my_work_folder}/tar_gz/encrypt/*.pgp'"
            }
        }

        stage('create archive directory') {
            options {
                timeout(time: 1, unit: 'MINUTES') 
            }
            steps {
                sh "ssh weblogic@c01p1-sftp04 'mkdir -p ${my_archive_folder}/${file_prefix}'"
            }
        }

        stage('archive file') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'if [ -n \"\$(ls -A ${my_work_folder}/tar_gz/${file_prefix}/*.gz 2>/dev/null)\" ]; then mv ${my_work_folder}/tar_gz/${file_prefix}*.gz ${my_archive_folder}/${file_prefix}/; else echo \"WARNING No files to archive.\"; fi'"
            }
        }

    }
    post {
        always {
            echo 'One way or another, I have finished'
        }
        success {
            echo "Success"
            // slackSend(color: '#00FF00', message: "SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        }
        failure {
            echo "Failure"
            // opsgenie(priority:"P2")
        }
    }
}
