
# AuditEventDto


## Properties

Name | Type
------------ | -------------
`id` | string
`ts` | Date
`tenantId` | string
`actorAccountId` | string
`actorRole` | string
`action` | string
`targetType` | string
`targetId` | string
`namespaceId` | string
`reason` | string
`requestId` | string
`outcome` | string
`details` | { [key: string]: any | null; }
`prevHash` | string
`entryHash` | string

## Example

```typescript
import type { AuditEventDto } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "ts": null,
  "tenantId": null,
  "actorAccountId": null,
  "actorRole": null,
  "action": null,
  "targetType": null,
  "targetId": null,
  "namespaceId": null,
  "reason": null,
  "requestId": null,
  "outcome": null,
  "details": null,
  "prevHash": null,
  "entryHash": null,
} satisfies AuditEventDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AuditEventDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


