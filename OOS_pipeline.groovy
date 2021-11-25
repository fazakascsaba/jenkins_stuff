//def work_folder="/weblogic_appdata/paymentsense/outgoing/statements"
//def my_archive_folder=weblogic_appdata/archive/paymentsense/outgoing/statements

def my_work_folder="/weblogic_appdata/tmp_csaba/statements"
def my_archive_folder="/weblogic_appdata/tmp_csaba/archive/statements"
def begins_with="Cover_note_"
def ends_with="pdf"
def file_prefix="Cover_note_All_PDF"
def oos_destination="Output/Acquirer/To_Richard"

pipeline {
    agent { label 'main' }
    stages {
        stage('Create directories') {
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
                sh "ssh weblogic@c01p1-sftp04 'mv ${my_work_folder}/tar_gz/*.pgp ${my_work_folder}/tar_gz/encrypt'"
            }
        }

        stage('copy files to OOS') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'oci os object bulk-upload -bn file-exchange --src-dir ${my_work_folder}/tar_gz/encrypt/ --no-overwrite --object-prefix ${oos_destination}/'"
            }
        }

        stage('remove pgp file') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'rm -f ${my_work_folder}/tar_gz/encrypt/*.pgp'"
            }
        }

        stage('create archive directory') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'mkdir -p ${my_archive_folder}/${file_prefix}'"
            }
        }

        stage('archive file') {
            steps {
                sh "ssh weblogic@c01p1-sftp04 'mv ${my_work_folder}/tar_gz/${file_prefix}*.gz ${my_archive_folder}/${file_prefix}/'"
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
