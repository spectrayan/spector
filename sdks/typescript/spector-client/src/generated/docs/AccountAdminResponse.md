
# AccountAdminResponse


## Properties

Name | Type
------------ | -------------
`accountId` | string
`username` | string
`displayName` | string
`email` | string
`tenantId` | string
`kind` | string
`profile` | string
`roles` | Set&lt;string&gt;
`scopes` | Set&lt;string&gt;
`active` | boolean
`locked` | boolean
`mustChangePassword` | boolean
`legalHold` | boolean
`defaultNamespaceId` | string
`ownerAccountId` | string
`ownerUsername` | string
`ownerDisplayName` | string
`quotas` | [AccountQuotaSummaryDto](AccountQuotaSummaryDto.md)
`namespaceCount` | number
`storageBytes` | number
`apiKeyCount` | number
`lastActivityAt` | Date
`ownedAgents` | [Array&lt;OwnedAgentSummaryDto&gt;](OwnedAgentSummaryDto.md)
`namespaceGrants` | [Array&lt;AccountNamespaceGrantDto&gt;](AccountNamespaceGrantDto.md)
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { AccountAdminResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "accountId": null,
  "username": null,
  "displayName": null,
  "email": null,
  "tenantId": null,
  "kind": null,
  "profile": null,
  "roles": null,
  "scopes": null,
  "active": null,
  "locked": null,
  "mustChangePassword": null,
  "legalHold": null,
  "defaultNamespaceId": null,
  "ownerAccountId": null,
  "ownerUsername": null,
  "ownerDisplayName": null,
  "quotas": null,
  "namespaceCount": null,
  "storageBytes": null,
  "apiKeyCount": null,
  "lastActivityAt": null,
  "ownedAgents": null,
  "namespaceGrants": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies AccountAdminResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AccountAdminResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


