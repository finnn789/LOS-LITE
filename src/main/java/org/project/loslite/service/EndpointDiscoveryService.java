package org.project.loslite.service;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.project.loslite.dto.ApiResponse;
import org.project.loslite.dto.EndpointDescriptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Predicate;

/**
 * "Peta" endpoint controller LOS-LITE buat konsumen eksternal (Auto-Layout) -
 * dipisah GET (kandidat sumber field form, response-nya yang dibaca) vs
 * POST/PUT/PATCH/DELETE (kandidat target tombol aksi), plus skeleton schema
 * (semua field simple jadi "") dari tipe response/request satu endpoint.
 * <p>
 * LOS-LITE gak punya konvensi path /detail atau /list kayak sistem lain,
 * makanya kategorisasi form-vs-aksi di sini berbasis HTTP method, bukan
 * suffix path.
 */
@Service
public class EndpointDiscoveryService {

    private static final String CONTROLLER_PACKAGE_PREFIX = "org.project.loslite.controller";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private final RequestMappingHandlerMapping handlerMapping;

    public EndpointDiscoveryService(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public List<EndpointDescriptor> listFormEndpoints() {
        return listEndpoints(methods -> methods.contains("GET"));
    }

    public List<EndpointDescriptor> listActionEndpoints() {
        return listEndpoints(methods -> !methods.contains("GET"));
    }

    private List<EndpointDescriptor> listEndpoints(Predicate<List<String>> methodFilter) {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> isControllerPackage(entry.getValue().getBeanType()))
                .flatMap(entry -> describeEndpoint(entry.getKey(), entry.getValue()).stream())
                .filter(descriptor -> methodFilter.test(List.of(descriptor.getHttpMethods().split(", "))))
                .sorted(Comparator.comparing(EndpointDescriptor::getController, Comparator.nullsFirst(String::compareTo)))
                .toList();
    }

    public ApiResponse<Map<String, Object>> getSchemaByPath(String path) {
        Optional<HandlerMethod> matched = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> isControllerPackage(entry.getValue().getBeanType()))
                .filter(entry -> extractPatterns(entry.getKey()).contains(path))
                .map(Map.Entry::getValue)
                .findFirst();

        if (matched.isEmpty()) {
            return ApiResponse.error("Endpoint dengan path " + path + " tidak ditemukan");
        }

        HandlerMethod handler = matched.get();
        Class<?> dataType = resolveRequestBodyType(handler);
        if (dataType == null) {
            dataType = resolveResponseType(handler);
        }

        if (dataType == null) {
            return ApiResponse.error("Gak bisa nentuin schema buat path " + path);
        }

        Map<String, Object> schema = buildSchema(dataType, new HashSet<>());
        return ApiResponse.success("Schema untuk path: " + path, schema);
    }

    private Class<?> resolveRequestBodyType(HandlerMethod handlerMethod) {
        for (Parameter parameter : handlerMethod.getMethod().getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                return parameter.getType();
            }
        }
        return null;
    }

    private Class<?> resolveResponseType(HandlerMethod handlerMethod) {
        return unwrapType(handlerMethod.getMethod().getGenericReturnType());
    }

    // Ngupas ResponseEntity<ApiResponse<X>> atau ResponseEntity<ApiResponse<List<X>>>
    // sampai ketemu X-nya. LOS-LITE selalu bungkus response lewat ApiResponse (lihat
    // javadoc kelas itu), jadi X selalu ada di generic argument pertama tiap lapis.
    private Class<?> unwrapType(Type type) {
        if (!(type instanceof ParameterizedType pt)) {
            return null;
        }

        Type rawType = pt.getRawType();
        if ((rawType == ResponseEntity.class || rawType == ApiResponse.class
                || (rawType instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass)))
                && pt.getActualTypeArguments().length > 0) {
            Type inner = pt.getActualTypeArguments()[0];
            return inner instanceof Class<?> cls ? cls : unwrapType(inner);
        }

        return null;
    }

    private Map<String, Object> buildSchema(Class<?> type, Set<Class<?>> visited) {
        if (type == null || isSimpleType(type) || visited.contains(type)) {
            return new LinkedHashMap<>();
        }
        Set<Class<?>> newVisited = new HashSet<>(visited);
        newVisited.add(type);

        Map<String, Object> map = new LinkedHashMap<>();
        BeanDescription desc = MAPPER.getSerializationConfig().introspect(MAPPER.constructType(type));

        for (BeanPropertyDefinition prop : desc.findProperties()) {
            String name = prop.getName();
            if (name == null || "class".equals(name) || prop.getPrimaryMember() == null) {
                continue;
            }
            map.put(name, processPropertyType(prop.getPrimaryMember().getType(), newVisited));
        }

        return map;
    }

    private Object processPropertyType(JavaType javaType, Set<Class<?>> visited) {
        Class<?> rawClass = javaType.getRawClass();

        if (isSimpleType(rawClass)) {
            return "";
        }
        if (Collection.class.isAssignableFrom(rawClass)) {
            JavaType contentType = javaType.getContentType();
            if (contentType == null || isSimpleType(contentType.getRawClass())) {
                return List.of("");
            }
            Map<String, Object> nested = buildSchema(contentType.getRawClass(), visited);
            return nested.isEmpty() ? new ArrayList<>() : List.of(nested);
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> nested = buildSchema(rawClass, visited);
        return nested.isEmpty() ? "" : nested;
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || BigDecimal.class.isAssignableFrom(type)
                || Boolean.class.isAssignableFrom(type)
                || Character.class.isAssignableFrom(type)
                || Enum.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type)
                || UUID.class.isAssignableFrom(type);
    }

    private boolean isControllerPackage(Class<?> beanType) {
        return beanType != null && beanType.getPackageName().startsWith(CONTROLLER_PACKAGE_PREFIX);
    }

    private List<EndpointDescriptor> describeEndpoint(RequestMappingInfo info, HandlerMethod handlerMethod) {
        Set<String> patterns = extractPatterns(info);

        List<String> httpMethodsList = new ArrayList<>();
        info.getMethodsCondition().getMethods().forEach(method -> httpMethodsList.add(method.name()));
        List<String> resolvedHttpMethods = httpMethodsList.isEmpty() ? List.of("ANY") : httpMethodsList;
        String httpMethodString = String.join(", ", resolvedHttpMethods);

        List<EndpointDescriptor> list = new ArrayList<>();
        for (String pattern : patterns) {
            EndpointDescriptor descriptor = new EndpointDescriptor();
            descriptor.setPath(pattern);
            descriptor.setHttpMethods(httpMethodString);
            descriptor.setController(handlerMethod.getBeanType().getSimpleName());
            descriptor.setHandler(handlerMethod.getMethod().getName());
            list.add(descriptor);
        }
        return list;
    }

    private Set<String> extractPatterns(RequestMappingInfo info) {
        var patternsCondition = info.getPatternsCondition();
        if (patternsCondition != null && !patternsCondition.getPatterns().isEmpty()) {
            return patternsCondition.getPatterns();
        }

        var pathPatternsCondition = info.getPathPatternsCondition();
        if (pathPatternsCondition != null && !pathPatternsCondition.getPatternValues().isEmpty()) {
            return pathPatternsCondition.getPatternValues();
        }

        return Collections.singleton("/");
    }
}
