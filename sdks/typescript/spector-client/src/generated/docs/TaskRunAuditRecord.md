
# TaskRunAuditRecord


## Properties

Name | Type
------------ | -------------
`runId` | string
`taskId` | string
`namespaceId` | string
`startTime` | Date
`endTime` | Date
`duration` | string
`status` | string
`result` | any
`errorMessage` | string
`success` | boolean

## Example

```typescript
import type { TaskRunAuditRecord } from ''

// TODO: Update the object below with actual values
const example = {
  "runId": null,
  "taskId": null,
  "namespaceId": null,
  "startTime": null,
  "endTime": null,
  "duration": null,
  "status": null,
  "result": null,
  "errorMessage": null,
  "success": null,
} satisfies TaskRunAuditRecord

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TaskRunAuditRecord
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


