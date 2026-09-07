
# AccountIntrospectResponse


## Properties

Name | Type
------------ | -------------
`accountId` | string
`displayName` | string
`kind` | string
`profile` | string
`defaultNamespaceId` | string
`quotas` | [AccountQuotas](AccountQuotas.md)
`flags` | [AccountFlags](AccountFlags.md)
`tenantId` | string
`legalHold` | boolean
`slugMap` | { [key: string]: string; }
`namespaces` | [Array&lt;NamespaceResponse&gt;](NamespaceResponse.md)
`activeGrants` | [Array&lt;GrantResponse&gt;](GrantResponse.md)
`soulVersion` | number

## Example

```typescript
import type { AccountIntrospectResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "accountId": null,
  "displayName": null,
  "kind": null,
  "profile": null,
  "defaultNamespaceId": null,
  "quotas": null,
  "flags": null,
  "tenantId": null,
  "legalHold": null,
  "slugMap": null,
  "namespaces": null,
  "activeGrants": null,
  "soulVersion": null,
} satisfies AccountIntrospectResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AccountIntrospectResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


