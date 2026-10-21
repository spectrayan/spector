
# AccountNamespaceGrantDto


## Properties

Name | Type
------------ | -------------
`grantId` | string
`namespaceId` | string
`namespaceSlug` | string
`namespaceDisplayName` | string
`namespaceOwnerId` | string
`role` | string
`actions` | Array&lt;string&gt;
`grantedBy` | string
`grantedAt` | Date
`expiresAt` | Date

## Example

```typescript
import type { AccountNamespaceGrantDto } from ''

// TODO: Update the object below with actual values
const example = {
  "grantId": null,
  "namespaceId": null,
  "namespaceSlug": null,
  "namespaceDisplayName": null,
  "namespaceOwnerId": null,
  "role": null,
  "actions": null,
  "grantedBy": null,
  "grantedAt": null,
  "expiresAt": null,
} satisfies AccountNamespaceGrantDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AccountNamespaceGrantDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


