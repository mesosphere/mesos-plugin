# Stage: Build
FROM gradle:5.4.1-jdk8 AS build
ADD . /home/gradle/project
WORKDIR /home/gradle/project
RUN gradle jpi


# Stage: Prod
FROM jenkins/jenkins:2.164 AS prod

# Default policy according to https://wiki.jenkins.io/display/JENKINS/Configuring+Content+Security+Policy
ENV JENKINS_CSP_OPTS="sandbox; default-src 'none'; img-src 'self'; style-src 'self';"

# Install plugin
ENV JENKINS_REF=/usr/share/jenkins/ref/
COPY --from=build /home/gradle/project/build/libs/mesos.hpi "${JENKINS_REF}/plugins/mesos.hpi"

# Disable first-run wizard
RUN echo 2.0 > "${JENKINS_REF}/jenkins.install.UpgradeWizard.state"

# DC/OS specific build.
FROM prod AS dcos

USER root

# Bootstrap scripts
COPY scripts/bootstrap.py /usr/local/jenkins/bin/bootstrap.py

# Setup nginx
RUN apt-get update && apt-get install -y nginx python
RUN mkdir -p /var/log/nginx/jenkins
COPY conf/nginx/nginx.conf /etc/nginx/nginx.conf

CMD /usr/local/jenkins/bin/bootstrap.py              \
   && nginx && java ${JVM_OPTS}                      \
     -Dhudson.model.DirectoryBrowserSupport.CSP="${JENKINS_CSP_OPTS}" \
     -Dhudson.udp=-1                                 \
     -Djava.awt.headless=true                        \
     -Dhudson.DNSMultiCast.disabled=true             \
     -Djenkins.install.runSetupWizard=false          \
     -jar ${JENKINS_FOLDER}/jenkins.war              \
     ${JENKINS_OPTS}                                 \
     --httpPort=${PORT1}                             \
     --webroot=${JENKINS_FOLDER}/war                 \
     --ajp13Port=-1                                  \
     --httpListenAddress=127.0.0.1                   \
     --ajp13ListenAddress=127.0.0.1                  \
     --prefix=${JENKINS_CONTEXT}
