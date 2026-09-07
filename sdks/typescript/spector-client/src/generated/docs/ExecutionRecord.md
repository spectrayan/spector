
# ExecutionRecord


## Properties

Name | Type
------------ | -------------
`traceId` | string
`routeId` | string
`tenantId` | string
`status` | string
`documentsProcessed` | number
`errors` | number
`duration` | string
`startedAt` | Date
`errorMessage` | string

## Example

```typescript
import type { ExecutionRecord } from ''

// TODO: Update the object below with actual values
const example = {
  "traceId": null,
  "routeId": null,
  "tenantId": null,
  "status": null,
  "documentsProcessed": null,
  "errors": null,
  "duration": null,
  "startedAt": null,
  "errorMessage": null,
} satisfies ExecutionRecord

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ExecutionRecord
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


