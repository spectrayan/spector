# TaskManagementControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getRecentAuditHistory**](TaskManagementControllerApi.md#getrecentaudithistory) | **GET** /api/v1/tasks/audit |  |
| [**getTask**](TaskManagementControllerApi.md#gettask) | **GET** /api/v1/tasks/{id} |  |
| [**getTaskAuditHistory**](TaskManagementControllerApi.md#gettaskaudithistory) | **GET** /api/v1/tasks/{id}/audit |  |
| [**listTasks**](TaskManagementControllerApi.md#listtasks) | **GET** /api/v1/tasks |  |
| [**pauseTask**](TaskManagementControllerApi.md#pausetask) | **POST** /api/v1/tasks/{id}/pause |  |
| [**rescheduleCron**](TaskManagementControllerApi.md#reschedulecronoperation) | **POST** /api/v1/tasks/{id}/reschedule-cron |  |
| [**rescheduleInterval**](TaskManagementControllerApi.md#rescheduleintervaloperation) | **POST** /api/v1/tasks/{id}/reschedule-interval |  |
| [**resumeTask**](TaskManagementControllerApi.md#resumetask) | **POST** /api/v1/tasks/{id}/resume |  |
| [**triggerTask**](TaskManagementControllerApi.md#triggertask) | **POST** /api/v1/tasks/{id}/trigger |  |



## getRecentAuditHistory

> Array&lt;TaskRunAuditRecord&gt; getRecentAuditHistory(limit)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { GetRecentAuditHistoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // number (optional)
    limit: 56,
  } satisfies GetRecentAuditHistoryRequest;

  try {
    const data = await api.getRecentAuditHistory(body);
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
| **limit** | `number` |  | [Optional] [Defaults to `50`] |

### Return type

[**Array&lt;TaskRunAuditRecord&gt;**](TaskRunAuditRecord.md)

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


## getTask

> TaskStatus getTask(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { GetTaskRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies GetTaskRequest;

  try {
    const data = await api.getTask(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**TaskStatus**](TaskStatus.md)

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


## getTaskAuditHistory

> Array&lt;TaskRunAuditRecord&gt; getTaskAuditHistory(id, limit)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { GetTaskAuditHistoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // string
    id: id_example,
    // number (optional)
    limit: 56,
  } satisfies GetTaskAuditHistoryRequest;

  try {
    const data = await api.getTaskAuditHistory(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **limit** | `number` |  | [Optional] [Defaults to `20`] |

### Return type

[**Array&lt;TaskRunAuditRecord&gt;**](TaskRunAuditRecord.md)

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


## listTasks

> Array&lt;TaskStatus&gt; listTasks()



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { ListTasksRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  try {
    const data = await api.listTasks();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**Array&lt;TaskStatus&gt;**](TaskStatus.md)

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


## pauseTask

> TaskActionResponse pauseTask(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { PauseTaskRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies PauseTaskRequest;

  try {
    const data = await api.pauseTask(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**TaskActionResponse**](TaskActionResponse.md)

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


## rescheduleCron

> TaskActionResponse rescheduleCron(id, rescheduleCronRequest)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { RescheduleCronOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // string
    id: id_example,
    // RescheduleCronRequest
    rescheduleCronRequest: ...,
  } satisfies RescheduleCronOperationRequest;

  try {
    const data = await api.rescheduleCron(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **rescheduleCronRequest** | [RescheduleCronRequest](RescheduleCronRequest.md) |  | |

### Return type

[**TaskActionResponse**](TaskActionResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## rescheduleInterval

> TaskActionResponse rescheduleInterval(id, rescheduleIntervalRequest)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { RescheduleIntervalOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // string
    id: id_example,
    // RescheduleIntervalRequest
    rescheduleIntervalRequest: ...,
  } satisfies RescheduleIntervalOperationRequest;

  try {
    const data = await api.rescheduleInterval(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **rescheduleIntervalRequest** | [RescheduleIntervalRequest](RescheduleIntervalRequest.md) |  | |

### Return type

[**TaskActionResponse**](TaskActionResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## resumeTask

> TaskActionResponse resumeTask(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { ResumeTaskRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies ResumeTaskRequest;

  try {
    const data = await api.resumeTask(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**TaskActionResponse**](TaskActionResponse.md)

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


## triggerTask

> TaskActionResponse triggerTask(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { TriggerTaskRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TaskManagementControllerApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies TriggerTaskRequest;

  try {
    const data = await api.triggerTask(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**TaskActionResponse**](TaskActionResponse.md)

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

