
# RouteConfig


## Properties

Name | Type
------------ | -------------
`id` | string
`name` | string
`templateId` | string
`connectorType` | string
`tenantId` | string
`source` | string
`schedule` | string
`properties` | { [key: string]: string; }
`credentialRef` | string
`routeYaml` | string
`status` | string
`enabled` | boolean
`createdAt` | Date

## Example

```typescript
import type { RouteConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "name": null,
  "templateId": null,
  "connectorType": null,
  "tenantId": null,
  "source": null,
  "schedule": null,
  "properties": null,
  "credentialRef": null,
  "routeYaml": null,
  "status": null,
  "enabled": null,
  "createdAt": null,
} satisfies RouteConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RouteConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


