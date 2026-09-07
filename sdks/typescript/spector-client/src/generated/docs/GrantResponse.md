
# GrantResponse


## Properties

Name | Type
------------ | -------------
`grantId` | string
`granteeAccountId` | string
`namespaceId` | string
`role` | string
`grantedBy` | string
`grantedAt` | Date
`expiresAt` | Date
`constraints` | [GrantConstraints](GrantConstraints.md)

## Example

```typescript
import type { GrantResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "grantId": null,
  "granteeAccountId": null,
  "namespaceId": null,
  "role": null,
  "grantedBy": null,
  "grantedAt": null,
  "expiresAt": null,
  "constraints": null,
} satisfies GrantResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GrantResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


