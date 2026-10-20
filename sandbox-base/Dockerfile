# Shared Sandbox base image for every Java Track (ADR 0003).
# Build context is the repo root, so this can COPY the helpers and the template.
#
#   docker buildx build --platform linux/amd64,linux/arm64 \
#     -f sandbox-base/Dockerfile -t ghcr.io/nadvolod/devdays-java-sandbox-base:latest --push .
#
# Both platforms: Instruqt needs linux/amd64 (an arm64 image fails to start there
# with an empty log and no error), and a native arm64 image lets the Workshop
# images be built and tested at full speed on Apple Silicon.
#
# A Workshop's sandbox/Dockerfile is FROM this image pinned by digest, and adds
# only its exercise/ and solution/ trees.

FROM eclipse-temurin:21-jdk-noble

RUN apt-get update && apt-get install -y --no-install-recommends \
      curl wget git vim nano less unzip xz-utils ca-certificates bash-completion jq procps python3 \
    && rm -rf /var/lib/apt/lists/*

# Set by BuildKit per platform. A default here would override it and put amd64
# binaries in the arm64 image.
ARG TARGETARCH

# --- Maven --------------------------------------------------------------------
ARG MAVEN_VERSION=3.9.11
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

# --- Temporal CLI -------------------------------------------------------------
ARG TEMPORAL_CLI_VERSION=1.9.1
RUN curl -fsSL \
      "https://github.com/temporalio/cli/releases/download/v${TEMPORAL_CLI_VERSION}/temporal_cli_${TEMPORAL_CLI_VERSION}_linux_${TARGETARCH:-amd64}.tar.gz" \
    | tar -xz -C /usr/local/bin temporal \
    && chmod +x /usr/local/bin/temporal

# --- watchexec (run-worker restarts the Worker on save) -----------------------
ARG WATCHEXEC_VERSION=2.8.0
RUN ARCH="$([ "$TARGETARCH" = "arm64" ] && echo aarch64 || echo x86_64)" \
    && curl -fsSL "https://github.com/watchexec/watchexec/releases/download/v${WATCHEXEC_VERSION}/watchexec-${WATCHEXEC_VERSION}-${ARCH}-unknown-linux-musl.tar.xz" \
    | tar -xJ -C /tmp \
    && mv /tmp/watchexec-*/watchexec /usr/local/bin/ \
    && rm -rf /tmp/watchexec-*

# --- code-server (VS Code in the browser, the Exercise tab) -------------------
ARG CODE_SERVER_VERSION=4.104.3
RUN curl -fsSL https://code-server.dev/install.sh | sh -s -- --version ${CODE_SERVER_VERSION}

# One editor instance rooted at the Workshop folder. security.workspace.trust.enabled
# MUST be false: with it on, code-server shows a blank page until the folder is
# trusted. The secondary side bar (the Chat pane on the right) opens by default
# and learners had to close it by hand, so it starts hidden and the Chat view is
# off. files.autoSave is load-bearing: the assignments say the editor saves as
# you type, and run-worker restarts the Worker on save. target-check/ is the
# Check's private build folder (workshop-check), hidden with target/.
RUN mkdir -p /root/.cs-workshop/User && printf '%s\n' \
      '{' \
      '    "security.workspace.trust.enabled": false,' \
      '    "workbench.colorTheme": "Default Dark Modern",' \
      '    "workbench.startupEditor": "none",' \
      '    "workbench.editor.enablePreview": false,' \
      '    "workbench.secondarySideBar.defaultVisibility": "hidden",' \
      '    "chat.disableAIFeatures": true,' \
      '    "editor.fontSize": 14,' \
      '    "editor.tabSize": 4,' \
      '    "editor.minimap.enabled": false,' \
      '    "editor.stickyScroll.enabled": true,' \
      '    "files.autoSave": "afterDelay",' \
      '    "files.autoSaveDelay": 500,' \
      '    "telemetry.telemetryLevel": "off",' \
      '    "redhat.telemetry.enabled": false,' \
      '    "explorer.compactFolders": false,' \
      '    "files.exclude": { "**/target": true, "**/target-check": true, "**/.classpath": true, "**/.project": true, "**/.settings": true, "**/.factorypath": true, "**/*.bak": false },' \
      '    "java.configuration.updateBuildConfiguration": "automatic",' \
      '    "java.import.gradle.enabled": false,' \
      '    "java.server.launchMode": "Standard",' \
      '    "java.jdt.ls.vmargs": "-XX:+UseParallelGC -Xmx1G -Xms256m",' \
      '    "java.help.firstView": "none",' \
      '    "java.showBuildStatusOnStart.enabled": "off",' \
      '    "java.compile.nullAnalysis.mode": "disabled",' \
      '    "java.maven.offline": true,' \
      '    "errorLens.enabledDiagnosticLevels": ["error"],' \
      '    "errorLens.messageMaxChars": 0' \
      '}' > /root/.cs-workshop/User/settings.json

# From Open VSX. Red Hat's Java extension gives inline errors and completion;
# Error Lens prints errors on the line. The Java language server needs ~1 GB,
# so a Track's config.yml gives the container 6144 MB.
ARG REDHAT_JAVA_VERSION=1.58.0
RUN code-server --user-data-dir /root/.cs-workshop --install-extension "redhat.java@${REDHAT_JAVA_VERSION}" \
    && for ext in usernamehw.errorlens; do \
      code-server --user-data-dir /root/.cs-workshop --install-extension "$ext" \
        || echo "==> WARNING: $ext did not install; the editor still works without it."; \
    done

# --- Check, Skip and run helpers (ADR 0002) -----------------------------------
COPY sandbox-base/bin/ /usr/local/bin/
RUN chmod +x /usr/local/bin/workshop-check /usr/local/bin/workshop-solve \
      /usr/local/bin/run-worker /usr/local/bin/run-starter

# --- Pre-warm ~/.m2 -----------------------------------------------------------
# Every Workshop uses the same SDK, test, logging and plugin versions as the
# template, so resolving and testing the template's Solution once here fills the
# cache that the Workshop images (and the offline Checks) rely on. The exec
# plugin is proven offline by running it on a class that exits at once. The
# build fails if any of the commands the lab runs would need the network.
COPY workshops/_template/solution/ /tmp/warm/
WORKDIR /tmp/warm
RUN printf 'public class Warm { public static void main(String[] a) {} }\n' > src/main/java/Warm.java \
    && mvn -B -q dependency:go-offline \
    && mvn -B -q test \
    && mvn -B -q -o compile exec:java@starter -Dstarter.main=Warm \
    && workshop-check /tmp/warm 1 \
    && cd / && rm -rf /tmp/warm

# --- Shell environment --------------------------------------------------------
RUN echo 'export TEMPORAL_ADDRESS="${TEMPORAL_ADDRESS:-127.0.0.1:7233}"' >> /root/.bashrc \
    && echo 'export MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"' >> /root/.bashrc

WORKDIR /root
CMD ["/bin/bash"]
