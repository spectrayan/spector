
# TenantAuditResponse


## Properties

Name | Type
------------ | -------------
`auditId` | string
`timestamp` | Date
`actorId` | string
`actorTenantId` | string
`action` | string
`targetType` | string
`targetId` | string
`details` | { [key: string]: any | null; }

## Example

```typescript
import type { TenantAuditResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "auditId": null,
  "timestamp": null,
  "actorId": null,
  "actorTenantId": null,
  "action": null,
  "targetType": null,
  "targetId": null,
  "details": null,
} satisfies TenantAuditResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantAuditResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


