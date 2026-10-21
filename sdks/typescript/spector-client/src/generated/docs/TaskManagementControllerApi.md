# TaskManagementControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getRecentAuditHistory**](TaskManagementControllerApi.md#getrecentaudithistory) | **GET** /tasks/audit |  |
| [**getRecentAuditHistory1**](TaskManagementControllerApi.md#getrecentaudithistory1) | **GET** /api/v1/tasks/audit |  |
| [**getTask**](TaskManagementControllerApi.md#gettask) | **GET** /tasks/{id} |  |
| [**getTask1**](TaskManagementControllerApi.md#gettask1) | **GET** /api/v1/tasks/{id} |  |
| [**getTaskAuditHistory**](TaskManagementControllerApi.md#gettaskaudithistory) | **GET** /api/v1/tasks/{id}/audit |  |
| [**getTaskAuditHistory1**](TaskManagementControllerApi.md#gettaskaudithistory1) | **GET** /tasks/{id}/audit |  |
| [**listTasks**](TaskManagementControllerApi.md#listtasks) | **GET** /api/v1/tasks |  |
| [**listTasks1**](TaskManagementControllerApi.md#listtasks1) | **GET** /tasks |  |
| [**pauseTask**](TaskManagementControllerApi.md#pausetask) | **POST** /tasks/{id}/pause |  |
| [**pauseTask1**](TaskManagementControllerApi.md#pausetask1) | **POST** /api/v1/tasks/{id}/pause |  |
| [**rescheduleCron**](TaskManagementControllerApi.md#reschedulecronoperation) | **POST** /api/v1/tasks/{id}/reschedule-cron |  |
| [**rescheduleCron1**](TaskManagementControllerApi.md#reschedulecron1) | **POST** /tasks/{id}/reschedule-cron |  |
| [**rescheduleInterval**](TaskManagementControllerApi.md#rescheduleintervaloperation) | **POST** /tasks/{id}/reschedule-interval |  |
| [**rescheduleInterval1**](TaskManagementControllerApi.md#rescheduleinterval1) | **POST** /api/v1/tasks/{id}/reschedule-interval |  |
| [**resumeTask**](TaskManagementControllerApi.md#resumetask) | **POST** /tasks/{id}/resume |  |
| [**resumeTask1**](TaskManagementControllerApi.md#resumetask1) | **POST** /api/v1/tasks/{id}/resume |  |
| [**triggerTask**](TaskManagementControllerApi.md#triggertask) | **POST** /tasks/{id}/trigger |  |
| [**triggerTask1**](TaskManagementControllerApi.md#triggertask1) | **POST** /api/v1/tasks/{id}/trigger |  |



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


## getRecentAuditHistory1

> Array&lt;TaskRunAuditRecord&gt; getRecentAuditHistory1(limit)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { GetRecentAuditHistory1Request } from '';

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
  } satisfies GetRecentAuditHistory1Request;

  try {
    const data = await api.getRecentAuditHistory1(body);
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


## getTask1

> TaskStatus getTask1(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { GetTask1Request } from '';

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
  } satisfies GetTask1Request;

  try {
    const data = await api.getTask1(body);
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


## getTaskAuditHistory1

> Array&lt;TaskRunAuditRecord&gt; getTaskAuditHistory1(id, limit)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { GetTaskAuditHistory1Request } from '';

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
  } satisfies GetTaskAuditHistory1Request;

  try {
    const data = await api.getTaskAuditHistory1(body);
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


## listTasks1

> Array&lt;TaskStatus&gt; listTasks1()



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { ListTasks1Request } from '';

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
    const data = await api.listTasks1();
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


## pauseTask1

> TaskActionResponse pauseTask1(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { PauseTask1Request } from '';

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
  } satisfies PauseTask1Request;

  try {
    const data = await api.pauseTask1(body);
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


## rescheduleCron1

> TaskActionResponse rescheduleCron1(id, rescheduleCronRequest)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { RescheduleCron1Request } from '';

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
  } satisfies RescheduleCron1Request;

  try {
    const data = await api.rescheduleCron1(body);
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


## rescheduleInterval1

> TaskActionResponse rescheduleInterval1(id, rescheduleIntervalRequest)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { RescheduleInterval1Request } from '';

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
  } satisfies RescheduleInterval1Request;

  try {
    const data = await api.rescheduleInterval1(body);
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


## resumeTask1

> TaskActionResponse resumeTask1(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { ResumeTask1Request } from '';

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
  } satisfies ResumeTask1Request;

  try {
    const data = await api.resumeTask1(body);
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


## triggerTask1

> TaskActionResponse triggerTask1(id)



### Example

```ts
import {
  Configuration,
  TaskManagementControllerApi,
} from '';
import type { TriggerTask1Request } from '';

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
  } satisfies TriggerTask1Request;

  try {
    const data = await api.triggerTask1(body);
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

