package dockerrunserver;

import dockerrun.api.DockerContainer;
import dockerrun.api.DockerContainerImpl;
import dockerrun.api.DockerRunService;
import foundation.url.sjvm.intrinsics.ServiceBridge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side implementation of DockerRunService that runs inside SJVM.
 *
 * The methods dispatch to the server via ServiceBridge.rpc() which SJVM intercepts
 * and routes to the host's RPC handler.
 */
public class DockerRunServiceClientImpl implements DockerRunService {

    public DockerRunServiceClientImpl() {
    }

    public DockerContainer startContainer(
            String imageReference,
            Map<String, String> environmentVariables,
            long autoTerminateSeconds) {
        Map<String, Object> params = new HashMap<>();
        params.put("imageReference", imageReference);
        params.put("environmentVariables", environmentVariables);
        params.put("autoTerminateSeconds", autoTerminateSeconds);
        Map<String, Object> result = ServiceBridge.rpc("startContainer", params);
        String uuid = String.valueOf(result.get("uuid"));
        return new DockerContainerImpl(uuid);
    }

    public Collection<DockerContainer> getAllContainers() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> result = ServiceBridge.rpc("getAllContainers", params);
        Object uuidsValue = result.get("uuids");
        if (uuidsValue == null) {
            return new ArrayList<>();
        }
        List<?> uuids = (List<?>) uuidsValue;
        ArrayList<DockerContainer> containers = new ArrayList<>();
        for (Object uuid : uuids) {
            if (uuid != null) {
                containers.add(new DockerContainerImpl(uuid.toString()));
            }
        }
        return containers;
    }

    public DockerContainer getContainer(UUID uuid) {
        return new DockerContainerImpl(uuid.toString());
    }

    public void pauseContainer(DockerContainer container) {
        Map<String, Object> params = new HashMap<>();
        params.put("uuid", container.getUuid().toString());
        ServiceBridge.rpc("pauseContainer", params);
    }

    public void unpauseContainer(DockerContainer container) {
        Map<String, Object> params = new HashMap<>();
        params.put("uuid", container.getUuid().toString());
        ServiceBridge.rpc("unpauseContainer", params);
    }

    public void terminateContainer(DockerContainer container) {
        Map<String, Object> params = new HashMap<>();
        params.put("uuid", container.getUuid().toString());
        ServiceBridge.rpc("terminateContainer", params);
    }
}
