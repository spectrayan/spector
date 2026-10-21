
# AdminNamespaceResponse


## Properties

Name | Type
------------ | -------------
`namespaceId` | string
`slug` | string
`displayName` | string
`description` | string
`type` | string
`status` | string
`legalHold` | boolean
`tenantId` | string
`ownerAccountId` | string
`ownerUsername` | string
`ownerDisplayName` | string
`grantsCount` | number
`memoryCount` | number
`storageBytes` | number
`quotas` | [NamespaceQuotasDto](NamespaceQuotasDto.md)
`createdAt` | Date
`lastWriteAt` | Date
`lastRecallAt` | Date

## Example

```typescript
import type { AdminNamespaceResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "namespaceId": null,
  "slug": null,
  "displayName": null,
  "description": null,
  "type": null,
  "status": null,
  "legalHold": null,
  "tenantId": null,
  "ownerAccountId": null,
  "ownerUsername": null,
  "ownerDisplayName": null,
  "grantsCount": null,
  "memoryCount": null,
  "storageBytes": null,
  "quotas": null,
  "createdAt": null,
  "lastWriteAt": null,
  "lastRecallAt": null,
} satisfies AdminNamespaceResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminNamespaceResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


