// Folders
//def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "SAMPLEPROJECT"
 
// Jobs
def buildMavenJob = freeStyleJob(projectFolderName + "/Project_Build_Maven_Marian")
def buildSonarJob = freeStyleJob(projectFolderName + "/Project_SonarQube_Marian")
def buildNexusSnapshotsJob = freeStyleJob(projectFolderName + "/Project_Snapshots_Marian")
def buildAnsibleJob = freeStyleJob(projectFolderName + "/Activity4_DeployProject_Ansible")
def buildSeleniumJob = freeStyleJob(projectFolderName + "/Project_Selenium_Marian")
def buildNexusReleasesJob = freeStyleJob(projectFolderName + "/ReleaseProject_Marian")
 
// Views
def pipelineView = buildPipelineView(projectFolderName + "/ProjectSimulation")
 
pipelineView.with{
    title('ProjectSimulation')
    displayedBuilds(10)
    selectedJob(projectFolderName + "/Project_Build_Maven_Marian")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

folder("${projectFolderName}"){
		displayName("${projectFolderName}")
		description("${projectFolderName}")
	}

buildMavenJob.with{


	scm {
		git {
			remote {
				credentials('jenkins (ADOP Jenkins Master)')
				url('http://gitlab/gitlab/mmcentino/CurrencyConverterDTS.git')
			}
				branch('*/master')
		}
	}

	wrappers {
		preBuildCleanup()
	}

	triggers {
		gitlabPush {
			buildOnMergeRequestEvents(true)
			buildOnPushEvents(true)
			enableCiSkip(true)
			setBuildDescription(false)
			rebuildOpenMergeRequest('never')
		}

		steps {
			maven {
				mavenInstallation('ADOP Maven')
				goals('package')
			}
		}

		publishers {
			archiveArtifacts('**/*.war')
			downstream('Project_SonarQube_Marian','SUCCESS')
		}

	}

}

buildSonarJob.with{

	scm {
		git {
			remote {
				credentials('jenkins (ADOP Jenkins Master)')
				url('http://gitlab/gitlab/mmcentino/CurrencyConverterDTS.git')
			}
				branch('*/master')
		}
	}


	configure { project ->
		project / 'builders' / 'hudson.plugins.sonar.SonarRunnerBuilder' {
				properties('''sonar.projectKey=sampleProjectKey
							sonar.projectName=sampleProjectName
							sonar.projectVersion=1
							sonar.sources=src/main/webapp''')
				javaOpts()
				jdk('(Inherit From Job)')
				task()
				}
			}

	wrappers{
		preBuildCleanup()
		}
  
  
  triggers {
    
	publishers {
		downstream('Project_Snapshots_Marian','SUCCESS')
	
    }
  }
}

buildNexusSnapshotsJob.with {

	steps {
		copyArtifacts('Project_Build_Maven_Marian'){
			includePatterns('target/*.war')
         buildSelector {
            	 latestSuccessful(true)
			}  
          	
       }
		nexusArtifactUploader {
		nexusVersion('NEXUS2')
		protocol('HTTP')
		nexusUrl('nexus:8081/nexus')
		groupId('DTSActivity')
		version('1')
		repository('snapshots')
       	credentialsId('MarianSnapshot')  
		
          artifact {
				artifactId('CurrencyConverter')
				type('war')
				file('/var/jenkins_home/jobs/SAMPLEPROJECT/jobs/Project_Build_Maven_Marian/workspace/target/CurrencyConverter.war')
			}  
		}
    }
     
	publishers {
		archiveArtifacts('**/*.war')
		}
    }

    triggers {
        publishers {
            downstream('Activity4_DeployProject_Ansible','SUCCESS')
        }
    }


// ansible
buildAnsibleJob.with {
    label('docker')
    scm{
        git{
            remote{
                credentials('adop-jenkins-master')
                url('http://gitlab/gitlab/mmcentino/ansible-deploy.git')
            }
            branch('*/master')
        }
    }
    wrappers {
        sshAgent('adop-jenkins-master')
        credentialsBinding {
            usernamePassword('username', 'password', 'mmcentino')
        }
    }

    steps {
        shell('ansible-playbook -i hosts master.yml -u ec2-user -e "image_version=$BUILD_NUMBER username=$username password=$password"')
    }
  
   triggers {
        publishers {
            downstream('Project_Selenium_Marian','SUCCESS')
        }
    }
} 

buildSeleniumJob.with {
    scm{
        git{
            remote{
                credentials('adop-jenkins-master')
                url('http://gitlab/gitlab/mmcentino/SeleniumDTS.git')
            }
            branch('*/master')
        }
    }
    steps {
            maven {
                mavenInstallation('ADOP Maven')
                goals('test')
            }
        }
    triggers {
        publishers {
            downstream('ReleaseProject_Marian','SUCCESS')
        }
    }
} 

// release
buildReleaseJob.with {
    steps {
        copyArtifacts('NEXUS') {
            includePatterns('**/*.war')
            buildSelector {
                latestSuccessful(true)
            }
        }

        nexusArtifactUploader {
            nexusVersion('NEXUS2')
            protocol('HTTP')
          nexusUrl('nexus:8081/nexus')
            credentialsId('mmcentino')
            groupId('DTSActivity')
            version('1')
            repository('releases')
            artifact {
                artifactId('CurrencyConverter')
                type('war')
                file('target/CurrencyConverter.war')
            }
        }
    }
}
