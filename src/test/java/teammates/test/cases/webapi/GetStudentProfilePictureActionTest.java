package teammates.test.cases.webapi;

import org.apache.http.HttpStatus;
import org.testng.annotations.Test;

import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.datatransfer.attributes.StudentAttributes;
import teammates.common.datatransfer.attributes.StudentProfileAttributes;
import teammates.common.util.Const;
import teammates.ui.webapi.action.GetStudentProfilePictureAction;
import teammates.ui.webapi.action.ImageResult;
import teammates.ui.webapi.action.JsonResult;
import teammates.ui.webapi.output.MessageOutput;

/**
 * SUT: {@link GetStudentProfilePictureAction}.
 */
public class GetStudentProfilePictureActionTest extends BaseActionTest<GetStudentProfilePictureAction> {

    @Override
    protected String getActionUri() {
        return Const.ResourceURIs.STUDENT_PROFILE_PICTURE;
    }

    @Override
    protected String getRequestMethod() {
        return GET;
    }

    @Override
    @Test
    public void testExecute() throws Exception {
        ______TS("Success case: student gets his own image");

        StudentAttributes student1InCourse1 = typicalBundle.students.get("student1InCourse1");
        loginAsStudent(student1InCourse1.googleId);

        GetStudentProfilePictureAction action = getAction();
        ImageResult imageResult = getImageResult(action);

        assertEquals(HttpStatus.SC_OK, imageResult.getStatusCode());

        // check image key is the same as well
        StudentProfileAttributes student1Profile = logic.getStudentProfile(student1InCourse1.googleId);
        assertEquals(student1Profile.pictureKey, imageResult.blobKey);

        ______TS("Success case: student passes in incomplete params but still gets his own image");

        String[] submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student1InCourse1.getCourse(),
        };

        action = getAction(submissionParams);
        imageResult = getImageResult(action);

        assertEquals(HttpStatus.SC_OK, imageResult.getStatusCode());

        submissionParams = new String[] {
                Const.ParamsNames.STUDENT_EMAIL, student1InCourse1.getEmail(),
        };

        action = getAction(submissionParams);
        imageResult = getImageResult(action);

        assertEquals(HttpStatus.SC_OK, imageResult.getStatusCode());
        assertEquals(student1Profile.pictureKey, imageResult.blobKey);

        ______TS("Success case: student gets his teammate's image");
        StudentAttributes student2InCourse1 = typicalBundle.students.get("student2InCourse1");
        gaeSimulation.logoutUser();
        loginAsStudent(student2InCourse1.googleId);

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student1InCourse1.getCourse(),
                Const.ParamsNames.STUDENT_EMAIL, student1InCourse1.getEmail(),
        };

        action = getAction(submissionParams);
        imageResult = getImageResult(action);

        assertEquals(HttpStatus.SC_OK, imageResult.getStatusCode());
        assertEquals(student1Profile.pictureKey, imageResult.blobKey);

        ______TS("Success case: instructor with privilege views image of his student");
        gaeSimulation.logoutUser();
        InstructorAttributes instructor1OfCourse1 = typicalBundle.instructors.get("instructor1OfCourse1");
        loginAsInstructor(instructor1OfCourse1.googleId);

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student1InCourse1.getCourse(),
                Const.ParamsNames.STUDENT_EMAIL, student1InCourse1.getEmail(),
        };

        action = getAction(submissionParams);
        imageResult = getImageResult(action);

        assertEquals(HttpStatus.SC_OK, imageResult.getStatusCode());
        assertEquals(student1Profile.pictureKey, imageResult.blobKey);

        ______TS("Failure case: requesting image of an unregistered student");

        StudentAttributes unregStudent = typicalBundle.students.get("student1InUnregisteredCourse");

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, unregStudent.getCourse(),
                Const.ParamsNames.STUDENT_EMAIL, unregStudent.getEmail(),
        };

        action = getAction(submissionParams);
        JsonResult jsonResult = getJsonResult(action);
        MessageOutput message = (MessageOutput) jsonResult.getOutput();

        assertEquals(HttpStatus.SC_NOT_FOUND, jsonResult.getStatusCode());
        assertEquals(GetStudentProfilePictureAction.PROFILE_PIC_NOT_FOUND, message.getMessage());

        ______TS("Failure case: requested student has no profile picture");

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student2InCourse1.getCourse(),
                Const.ParamsNames.STUDENT_EMAIL, student2InCourse1.getEmail(),
        };

        action = getAction(submissionParams);
        jsonResult = getJsonResult(action);
        message = (MessageOutput) jsonResult.getOutput();

        assertEquals(HttpStatus.SC_NOT_FOUND, jsonResult.getStatusCode());
        assertEquals(GetStudentProfilePictureAction.PROFILE_PIC_NOT_FOUND, message.getMessage());
    }

    @Test
    @Override
    protected void testAccessControl() throws Exception {
        StudentAttributes student1InCourse1 = typicalBundle.students.get("student1InCourse1");
        StudentAttributes student2InCourse1 = typicalBundle.students.get("student2InCourse1");
        StudentAttributes student1InCourse3 = typicalBundle.students.get("student1InCourse3");
        StudentAttributes student5InCourse1 = typicalBundle.students.get("student5InCourse1");

        ______TS("Failure case: student can only view his own team in the course");

        //student from another team
        gaeSimulation.logoutUser();
        loginAsStudent(student5InCourse1.googleId);

        String[] submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student1InCourse1.getCourse(),
                Const.ParamsNames.STUDENT_EMAIL, student1InCourse1.getEmail(),
        };

        verifyCannotAccess(submissionParams);

        //student from another course
        gaeSimulation.logoutUser();
        loginAsStudent(student1InCourse3.googleId);

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student1InCourse1.getCourse(),
                Const.ParamsNames.STUDENT_EMAIL, student1InCourse1.getEmail(),
        };

        verifyCannotAccess(submissionParams);

        ______TS("Success case: student can only view his own team in the course");

        gaeSimulation.logoutUser();
        loginAsStudent(student2InCourse1.googleId);

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student1InCourse1.getCourse(),
                Const.ParamsNames.STUDENT_EMAIL, student1InCourse1.getEmail(),
        };

        verifyCanAccess(submissionParams);

        ______TS("Success case: student can view his own photo but instructor or admin cannot");

        gaeSimulation.logoutUser();
        loginAsStudent(student1InCourse1.googleId);

        verifyCanAccess();
        verifyInaccessibleForInstructors();
        verifyInaccessibleForAdmin();

        ______TS("Success/Failure case: only instructors with privilege can view photo");

        gaeSimulation.logoutUser();
        verifyInaccessibleWithoutViewStudentInSectionsPrivilege(submissionParams);
        verifyInaccessibleForInstructorsOfOtherCourses(submissionParams);
        verifyAccessibleForInstructorsOfTheSameCourse(submissionParams);

        ______TS("Failure case: error in params (passing in non-existent email/id)");

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, "RANDOM_COURSE",
                Const.ParamsNames.STUDENT_EMAIL, student1InCourse1.getEmail(),
        };

        verifyInaccessibleForStudents(submissionParams);
        verifyInaccessibleForInstructors(submissionParams);
        verifyInaccessibleForAdmin(submissionParams);

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, student1InCourse1.getId(),
                Const.ParamsNames.STUDENT_EMAIL, "RANDOM_EMAIL",
        };

        verifyInaccessibleForStudents(submissionParams);
        verifyInaccessibleForInstructors(submissionParams);
        verifyInaccessibleForAdmin(submissionParams);
    }
}
