
# BreakGlassResponse


## Properties

Name | Type
------------ | -------------
`id` | string
`tenantId` | string
`namespaceId` | string
`ownerAccountId` | string
`requesterAccountId` | string
`requesterRole` | string
`justification` | string
`ticketRef` | string
`durationMinutes` | number
`status` | string
`approverAccountId` | string
`approvalComment` | string
`grantId` | string
`accessScope` | string
`exportPermitted` | boolean
`mcpPermitted` | boolean
`createdAt` | Date
`approvedAt` | Date
`expiresAt` | Date
`revokedAt` | Date
`revokedBy` | string
`revokeReason` | string

## Example

```typescript
import type { BreakGlassResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "tenantId": null,
  "namespaceId": null,
  "ownerAccountId": null,
  "requesterAccountId": null,
  "requesterRole": null,
  "justification": null,
  "ticketRef": null,
  "durationMinutes": null,
  "status": null,
  "approverAccountId": null,
  "approvalComment": null,
  "grantId": null,
  "accessScope": null,
  "exportPermitted": null,
  "mcpPermitted": null,
  "createdAt": null,
  "approvedAt": null,
  "expiresAt": null,
  "revokedAt": null,
  "revokedBy": null,
  "revokeReason": null,
} satisfies BreakGlassResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BreakGlassResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


