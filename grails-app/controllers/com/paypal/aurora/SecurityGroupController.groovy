package com.paypal.aurora

import com.paypal.aurora.exception.RestClientRequestException
import com.paypal.aurora.util.ConstraintsProcessor
import grails.converters.JSON
import grails.converters.XML


class SecurityGroupController {

    private final static IP_PROTOCOLS = ['TCP', 'UDP', 'ICMP']

    def static allowedMethods = [list: ['GET', 'POST'], show: ['GET', 'POST'], save: 'POST', delete: ['POST'], addRule: ['GET', 'POST'], deleteRule: ['GET', 'POST']]

    def securityGroupService

    def index = { redirect(action: 'list', params: params) }

    def list = {
        def error
        def securityGroups
        try{
            securityGroups = securityGroupService.getAllSecurityGroups()
        } catch (RestClientRequestException e){
            response.status = ExceptionUtils.getExceptionCode(e)
            error = ExceptionUtils.getExceptionMessage(e)
        }
        def model = [securityGroups : securityGroups, errors : error]
        withFormat {
            html { [securityGroups: securityGroups] }
            xml { new XML(model).render(response) }
            json { new JSON(model).render(response) }
        }
    }

    def show = {
        try {
            def securityGroup = securityGroupService.getSecurityGroupById(params.id)
            def model = [securityGroup : securityGroup]
            params.isDefault = securityGroup.isDefault()
            withFormat {
                html { [parent:"/securityGroup",securityGroup: securityGroup] }
                xml { new XML(model).render(response) }
                json { new JSON(model).render(response) }
            }
        } catch (RestClientRequestException e) {
            response.status = ExceptionUtils.getExceptionCode(e)
            def errors = ExceptionUtils.getExceptionMessage(e)
            withFormat {
                html { flash.message = errors; redirect(action: 'list')}
                xml { new XML([errors: errors]).render(response) }
                json { new JSON([errors: errors]).render(response) }
            }
        }
    }

    def create = {
        [parent:"/securityGroup", constraints: ConstraintsProcessor.getConstraints(SecurityCreateCommand.class)]
    }

    def editRules = {
        def securityGroup = securityGroupService.getSecurityGroupById(params.id)
        params.securityGroup = securityGroup
        params.isDefault = securityGroup.isDefault()
        params.ipProtocols = IP_PROTOCOLS
        params.sourceGroups = getSourceGroups()
        [parent: "/securityGroup/show/${params.id}",
                constraints: ConstraintsProcessor.getConstraints(RuleAddCommand.class) ]
    }

    def addRule = { RuleAddCommand cmd ->
        if (cmd.hasErrors()) {
            withFormat {
                html { chain(action: 'editRules', model: [cmd: cmd], params: params) }
                xml { new XML([errors:cmd.errors]).render(response) }
                json { new JSON([errors:cmd.errors]).render(response) }
            }
        } else {
            try {
                def resp = securityGroupService.createSecurityGroupRule(params)
                def model = [resp : resp]
                withFormat {
                    html { redirect(action: 'editRules', params: [id: params.id]) }
                    xml { new XML(model).render(response) }
                    json { new JSON(model).render(response) }
                }
            } catch (RestClientRequestException e) {
                response.status = ExceptionUtils.getExceptionCode(e)
                def errors = ExceptionUtils.getExceptionMessage(e)
                withFormat {
                    html { flash.message = errors; redirect(action: 'editRules', params: [id: params.id])}
                    xml { new XML([errors: errors]).render(response) }
                    json { new JSON([errors:flash.message]).render(response) }
                }
            }
        }
    }

    private def getSourceGroups() {
        def sourceGroups = ['0': 'CIDR']
        for (securityGroup in securityGroupService.getAllSecurityGroups()) {
            sourceGroups["${securityGroup.id}"] = securityGroup.name
        }
        sourceGroups
    }


    def deleteRule = {
        List<String> rulesIds = Requests.ensureList(params.selectedRules)
        def model = securityGroupService.deleteSecurityGroupRulesById(rulesIds)
        def flashMessage = ResponseUtils.defineMessageByList("Could not delete rules with id: ", model.notRemovedItems)
        response.status = ResponseUtils.defineResponseStatus(model, flashMessage)
        withFormat {
            html {
                flash.message = flashMessage;
                redirect(action: 'editRules', params: [id: params.id])
            }
            xml { new XML(model).render(response) }
            json { new JSON(model).render(response) }
        }
    }

    def save = { SecurityCreateCommand cmd ->
        if (cmd.hasErrors()) {
            withFormat {
                html { chain(action: 'create', model: [cmd: cmd], params: params) }
                xml { new XML([errors:cmd.errors]).render(response) }
                json { new JSON([errors:cmd.errors]).render(response) }
            }
        } else {
            try {
                def resp = securityGroupService.createSecurityGroup(params)
                def model = [resp : resp]
                withFormat {
                    html { redirect(action: 'list') }
                    xml { new XML(model).render(response) }
                    json { new JSON(model).render(response) }
                }
            } catch (RestClientRequestException e) {
                response.status = ExceptionUtils.getExceptionCode(e)
                def error = ExceptionUtils.getExceptionMessage(e)
                def model = [errors : error]
                withFormat {
                    html { flash.message = error; chain(action: 'create', params: params)}
                    xml { new XML(model).render(response) }
                    json { new JSON(model).render(response) }
                }
            }

        }
    }

    def delete = {
        try {
            def resp = securityGroupService.deleteSecurityGroupById(params.id)
            def model = [resp : resp]
            withFormat {
                html { redirect(action: 'list') }
                xml { new XML(model).render(response) }
                json { new JSON(model).render(response) }
            }
        } catch (RestClientRequestException e) {
            response.status = ExceptionUtils.getExceptionCode(e)
            def errors = ExceptionUtils.getExceptionMessage(e)
            withFormat {
                html { flash.message = errors; chain(action: 'show', params: params)}
                xml { new XML([errors: errors]).render(response) }
                json { new JSON([errors: errors]).render(response) }
            }
        }
    }

}

class SecurityCreateCommand {

    String name
    String description

    static constraints = {
        name(nullable: false, blank: false)
        description(nullable: false, blank: false)
    }
}

class RuleAddCommand {
    String fromPort
    String toPort

    static constraints = {
        fromPort(nullable: false, blank: false, matches: /\d+/)
        toPort(nullable: false, blank: false, matches: /\d+/)
    }

}
