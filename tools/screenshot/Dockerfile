# A real Minecraft 26.3 client for screenshots, rendered in software (Xvfb + Mesa llvmpipe).
# Run it on node1 only (see README.md); it is capped at 3 GB RAM and 3 CPUs by run-client.sh.
FROM eclipse-temurin:25-jdk
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      python3 xvfb xdotool x11-utils x11-apps netpbm mesa-utils libgl1 libgl1-mesa-dri libegl1 libgles2 \
      mesa-vulkan-drivers libvulkan1 libxcursor1 libxrandr2 libxi6 libxss1 libxkbcommon-x11-0 \
      libxext6 libxfixes3 libxinerama1 libasound2t64 libpulse0 \
 && rm -rf /var/lib/apt/lists/*
COPY fetch_client.py entry.sh /tools/
ENTRYPOINT ["/tools/entry.sh"]
