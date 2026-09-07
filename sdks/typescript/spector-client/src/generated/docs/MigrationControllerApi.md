# MigrationControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**exportMemory**](MigrationControllerApi.md#exportmemory) | **POST** /api/v1/migration/export |  |
| [**getJobStatus**](MigrationControllerApi.md#getjobstatus) | **GET** /api/v1/migration/jobs/{executionId} |  |
| [**importMemory**](MigrationControllerApi.md#importmemory) | **POST** /api/v1/migration/import |  |



## exportMemory

> { [key: string]: any | null; } exportMemory(outputPath, namespace)



### Example

```ts
import {
  Configuration,
  MigrationControllerApi,
} from '';
import type { ExportMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MigrationControllerApi(config);

  const body = {
    // string
    outputPath: outputPath_example,
    // string (optional)
    namespace: namespace_example,
  } satisfies ExportMemoryRequest;

  try {
    const data = await api.exportMemory(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **outputPath** | `string` |  | [Defaults to `undefined`] |
| **namespace** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getJobStatus

> { [key: string]: any | null; } getJobStatus(executionId)



### Example

```ts
import {
  Configuration,
  MigrationControllerApi,
} from '';
import type { GetJobStatusRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MigrationControllerApi(config);

  const body = {
    // number
    executionId: 789,
  } satisfies GetJobStatusRequest;

  try {
    const data = await api.getJobStatus(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **executionId** | `number` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## importMemory

> { [key: string]: any | null; } importMemory(bundlePath, targetNamespace)



### Example

```ts
import {
  Configuration,
  MigrationControllerApi,
} from '';
import type { ImportMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MigrationControllerApi(config);

  const body = {
    // string
    bundlePath: bundlePath_example,
    // string (optional)
    targetNamespace: targetNamespace_example,
  } satisfies ImportMemoryRequest;

  try {
    const data = await api.importMemory(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **bundlePath** | `string` |  | [Defaults to `undefined`] |
| **targetNamespace** | `string` |  | [Optional] [Defaults to `&#39;default&#39;`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

