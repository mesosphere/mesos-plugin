# BUILD
FROM gradle:5.4.1-jdk8 AS build
ADD . /home/gradle/project
WORKDIR /home/gradle/project

RUN gradle jpi


# IMAGE
FROM jenkins/jenkins:2.164

# Install plugin
ENV JENKINS_REF=/usr/share/jenkins/ref/
COPY --from=build /home/gradle/project/build/libs/mesos.hpi "${JENKINS_REF}/plugins/mesos.hpi"

# Disable first-run wizard
RUN echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state