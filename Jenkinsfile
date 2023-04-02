// Jenkins declarative pipeline syntax https://jenkins.io/doc/book/pipeline/syntax/
pipeline {

	agent {
		node {
			label 'saga-workbench && docker && agent_version_v2'
		}
	}

	parameters {
		// defaults to publish snapshot so SAGA development can depend on latest build if needed
		booleanParam(name: 'PERFORM_RELEASE', defaultValue: false, description: "Performs release setting version number, publish release artifacts and tags version")
		string (name: 'NEXT_DEVELOPMENT_VERSION_NUMBER', defaultValue: '', description: "Input next development version as MAJOR.MINOR.PATCH.")
	}

	options {
		// using the Timestamper plugin we can add timestamps to the console log
		timestamps()
		// disable resume pipeline when master restart - it will not work with all the containers running
		disableResume()
	}

	environment {
		// set specific version of our SAGA solution image we build so we ensure exactly that image version is tested later
		SAGA_SOLUTION_IMAGE_NAME = "saga-tabsa-solution"
		DEFAULT_CONFIG_EXAMPLE_TO_TEST = "default"
		SAGA_SOLUTION_PROBE_IMAGE_NAME = "saga-probe"
		SEEDING_CONFIG_EXAMPLE = "seeder"
		BUILD_IDENTIFIER = env.BRANCH_NAME.replaceAll("[^a-zA-Z0-9\\.\\_]", "-").toLowerCase()
		// Docker Compose set a label on all the resources it creates based on the project name, but instead of using the default which is
		// directory path, we will set it explicitly with -p PROJECTNAME to avoid Jenkins making incompatible directory names
		// for the build workspace. Notice to ensure clean-up, this should be unique only on per branch pattern, not per build.
		DOCKER_COMPOSE_PROJECT_NAME = "${BUILD_IDENTIFIER}"
		SAGA_SOLUTION_CONTAINER_LABEL = "com.persequor.product=saga-tabsa-solution"
 		MVN_BUILD_DOCKER_IMAGE_VERSION = "maven:3.6.3-openjdk-17"
		MVN_VERSIONS_FULL_NAME="org.codehaus.mojo:versions-maven-plugin:2.7"
		NETWORK = "${BUILD_IDENTIFIER}"
	}

	stages {
		stage('release') {
			when { expression { return params.PERFORM_RELEASE } }
			stages {
				stage('publish private') {
					steps {
						// Notice $HOME is interpreted by the build agent, when starting the container so it is the build agent user's HOME
						withDockerContainer(args: '-v $HOME/.m2:/var/maven/.m2 -v ${WORKSPACE}:/usr/src/mymaven -w /usr/src/mymaven -e MAVEN_CONFIG=/var/maven/.m2 -e MAVEN_OPTS=-Duser.home=/var/maven', image: env.MVN_BUILD_DOCKER_IMAGE_VERSION) {
							sh """
								cd jaxb-ri
								mvn -DskipTests=true -Dmaven.test.skip=true deploy
							"""
						}
					}
				}
			}
		}
	}
}
